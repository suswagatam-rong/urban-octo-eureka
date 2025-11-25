package com.example.ckyc.service;

import com.example.ckyc.Config;
import com.example.ckyc.CifRecord;
import com.example.ckyc.crypto.CryptoService;
import com.example.ckyc.crypto.KeyStoreKeyProvider;
import com.example.ckyc.dao.EnquiryAttemptDao;
import com.example.ckyc.dto.CersaiEnquiryResponse;
import com.example.ckyc.http.CersaiHttpClient;
import com.example.ckyc.XmlHelper;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.crypto.SecretKey;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.spec.*;
import javax.xml.crypto.dsig.keyinfo.*;
import java.io.*;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.sql.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class CifProcessor {
    private final Config cfg;
    private final CryptoService crypto;
    private final KeyStoreKeyProvider keyProvider;
    private final EnquiryAttemptDao dao;
    private final CersaiHttpClient client;
    private final Connection conn;
    private final String cersaiKeyAlias;
    private final String fiKeyAlias;
    private final String debugDir;

    public CifProcessor(Config cfg, CryptoService crypto, KeyStoreKeyProvider keyProvider, EnquiryAttemptDao dao, CersaiHttpClient client, Connection conn) {
        this.cfg = cfg; this.crypto = crypto; this.keyProvider = keyProvider; this.dao = dao; this.client = client; this.conn = conn;
        this.cersaiKeyAlias = cfg.get("ckyc.cersai.keyAlias");
        this.fiKeyAlias = cfg.get("ckyc.fi.keyAlias");
        this.debugDir = cfg.getOrDefault("debug.output.dir", "/tmp/ckyc_debug");
    }

    public void processSingleCif(String fiCode, String branchCode, String cifStatus, String cif) throws Exception {
        CifRecord rec = fetchCifRecordByCifBranchStatus(cif, branchCode, cifStatus);
        if (rec == null) { System.out.println("No such CIF found."); return; }
        processRecord(fiCode, branchCode, cifStatus, rec);
    }

    public void processByBranchAndStatus(String fiCode, String branchCode, String cifStatus) throws Exception {
        String sql = "SELECT cif, name, pan FROM ckyc_customer_details WHERE branch_code = ? AND cif_status = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, branchCode); ps.setString(2, cifStatus);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CifRecord r = new CifRecord();
                    r.setCif(rs.getString("cif"));
                    r.setName(rs.getString("name"));
                    r.setPan(rs.getString("pan"));
                    try { processRecord(fiCode, branchCode, cifStatus, r); } catch (Exception ex) { ex.printStackTrace(); }
                }
            }
        }
    }

    private void processRecord(String fiCode, String branchCode, String cifStatus, CifRecord record) throws Exception {
        List<Map.Entry<String,String>> ids = new ArrayList<>();
        if (record.getPan() != null && !record.getPan().isBlank()) ids.add(Map.entry("C", record.getPan()));
        Map<String,String> others = fetchOtherIds(record.getCif());
        if (others != null) for (Map.Entry<String,String> e : others.entrySet()) if (e.getValue()!=null && !e.getValue().isBlank()) ids.add(Map.entry(e.getKey(), e.getValue()));
        if (ids.isEmpty()) { System.out.println("No ID available for CIF " + record.getCif()); return; }

        PublicKey cersaiPub = keyProvider.getPublicKey(cersaiKeyAlias);
        PrivateKey fiPriv = keyProvider.getPrivateKey(fiKeyAlias);
        Certificate fiCert = keyProvider.getCertificate(fiKeyAlias);

        int attemptNo = 0;
        for (Map.Entry<String,String> id : ids) {
            attemptNo++;
            String idType = id.getKey();
            String idNumber = id.getValue();

            String pidDataXml = buildPidData(idType, idNumber, record);

            SecretKey sessionKey = crypto.generateSessionKey();
            String requestId = uniqueRequestIdForToday();
            byte[] aad = (record.getCif() + "|" + requestId).getBytes(StandardCharsets.UTF_8);

            byte[] encryptedPid = crypto.aesGcmEncrypt(pidDataXml.getBytes(StandardCharsets.UTF_8), sessionKey, aad);
            String pidB64 = CryptoService.b64(encryptedPid);
            byte[] wrappedKey = crypto.wrapKeyWithRsa(sessionKey, cersaiPub);
            String wrappedKeyB64 = CryptoService.b64(wrappedKey);

            Document reqDoc = buildRequestDom(fiCode, requestId, "1.3", pidB64, wrappedKeyB64);
            signXmlDocumentWithX509(reqDoc, fiPriv, fiCert);
            String signedXml = domToString(reqDoc);

            boolean selfOk = verifyXmlSignature(reqDoc, keyProvider.getPublicKey(fiKeyAlias));
            if (!selfOk) {
                System.err.println("Local signature verification failed. Dumping signed xml.");
                writeDebugFile("request_bad_" + requestId + ".xml", signedXml);
                continue;
            }

            long reqRow = dao.nextRequestId();
            String txnId = UUID.randomUUID().toString();
            dao.insertRequest(reqRow, record.getCif(), branchCode, cifStatus, idType, idNumber, attemptNo, txnId, encryptedPid, signedXml);

            CersaiHttpClient.Response resp = null; Exception lastEx = null;
            int tries = 0;
            while (tries++ < 3) {
                try { resp = client.postXml(signedXml); break; }
                catch (Exception ex) { lastEx = ex; Thread.sleep(200L * tries); }
            }
            if (resp == null) {
                dao.updateResponse(reqRow, null, null, null, null, lastEx != null ? lastEx.getMessage() : "HTTP_FAILED", "E");
                continue;
            }

            String respBody = resp.body;
            writeDebugFile("response_raw_" + requestId + ".xml", respBody);

            try {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                dbf.setNamespaceAware(true);
                DocumentBuilder db = dbf.newDocumentBuilder();
                Document respDoc = db.parse(new java.io.ByteArrayInputStream(respBody.getBytes(StandardCharsets.UTF_8)));

                boolean sigOk = verifyXmlSignature(respDoc, cersaiPub);
                if (!sigOk) {
                    dao.updateResponse(reqRow, null, respBody, null, resp.status, "SIG_INVALID", "E");
                    continue;
                }

                String respWrappedKeyB64 = getNodeText(respDoc, "/*/*[local-name()='SESSION_KEY' or name()='SESSION_KEY']/text()");
                String respPidB64 = getNodeText(respDoc, "/*/*[local-name()='PID' or name()='PID']/text()");

                if (respWrappedKeyB64 == null || respPidB64 == null) {
                    String err = getNodeText(respDoc, "/*/*[local-name()='ERROR' or name()='ERROR']/text()");
                    dao.updateResponse(reqRow, null, respBody, null, resp.status, err != null ? err : "INVALID_RESPONSE", "E");
                    continue;
                }

                SecretKey respSes = crypto.unwrapKeyWithRsa(CryptoService.b64d(respWrappedKeyB64), fiPriv);

                byte[] respEncPid = CryptoService.b64d(respPidB64);
                byte[] respPlain = null;
                try {
                    respPlain = crypto.aesGcmDecrypt(respEncPid, respSes, aad);
                } catch (Exception ex) {
                    try { respPlain = crypto.aesGcmDecrypt(respEncPid, respSes, null); }
                    catch (Exception ex2) { throw ex2; }
                }

                String pidXml = new String(respPlain, StandardCharsets.UTF_8);

                CersaiEnquiryResponse parsed = null;
                try { parsed = XmlHelper.unmarshal(pidXml, CersaiEnquiryResponse.class); } catch (Exception e) { /* ignore */ }

                String statusFlag = (parsed != null && parsed.getCkycNo() != null && !parsed.getCkycNo().isBlank()) ? "F" : "N";
                dao.updateResponse(reqRow, respEncPid, respBody, parsed, resp.status, "OK", statusFlag, parsed != null ? parsed.getRemarks() : null);

                if ("F".equals(statusFlag)) {
                    System.out.println("Found CKYC for CIF=" + record.getCif());
                    break;
                } else {
                    System.out.println("No CKYC for CIF=" + record.getCif() + " with ID " + idType);
                }

            } catch (Exception e) {
                dao.updateResponse(reqRow, null, respBody, null, resp.status, e.getMessage(), "E");
                e.printStackTrace();
            }
        }
    }

    private String buildPidData(String idType, String idNumber, CifRecord rec) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
        String dt = java.time.LocalDateTime.now().format(fmt);
        String idNoXml;
        if ("E".equalsIgnoreCase(idType)) {
            if (idNumber.contains("|")) idNoXml = idNumber;
            else {
                String name = rec.getName() == null ? "" : rec.getName().replaceAll("\\|"," ");
                String dob = "01-01-1970";
                String gender = "M";
                idNoXml = idNumber + "|" + name + "|" + dob + "|" + gender;
            }
        } else {
            idNoXml = idNumber;
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<PID_DATA>"
                + "<DATE_TIME>" + dt + "</DATE_TIME>"
                + "<ID_TYPE>" + escapeXml(idType) + "</ID_TYPE>"
                + "<ID_NO>" + escapeXml(idNoXml) + "</ID_NO>"
                + "</PID_DATA>";
    }

    private Document buildRequestDom(String fiCode, String requestId, String version, String pidB64, String sessionKeyB64) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.newDocument();
        Element root = doc.createElement("REQ_ROOT"); doc.appendChild(root);
        Element header = doc.createElement("HEADER");
        Element fi = doc.createElement("FI_CODE"); fi.setTextContent(fiCode);
        Element rid = doc.createElement("REQUEST_ID"); rid.setTextContent(requestId);
        Element ver = doc.createElement("VERSION"); ver.setTextContent(version);
        header.appendChild(fi); header.appendChild(rid); header.appendChild(ver);
        root.appendChild(header);
        Element ck = doc.createElement("CKYC_INQ");
        Element pid = doc.createElement("PID"); pid.setTextContent(pidB64);
        Element sk = doc.createElement("SESSION_KEY"); sk.setTextContent(sessionKeyB64);
        ck.appendChild(pid); ck.appendChild(sk);
        root.appendChild(ck);
        return doc;
    }

    private void signXmlDocumentWithX509(Document doc, PrivateKey signingKey, Certificate cert) throws Exception {
        XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");
        Transform env = fac.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null);
        Transform exc = fac.newTransform(CanonicalizationMethod.EXCLUSIVE, (TransformParameterSpec) null);
        List<Transform> transforms = Arrays.asList(env, exc);
        Reference ref = fac.newReference("", fac.newDigestMethod(DigestMethod.SHA256, null), transforms, null, null);
        SignedInfo si = fac.newSignedInfo(fac.newCanonicalizationMethod(CanonicalizationMethod.EXCLUSIVE, (C14NMethodParameterSpec)null), fac.newSignatureMethod(SignatureMethod.RSA_SHA256, null), Collections.singletonList(ref));
        KeyInfoFactory kif = fac.getKeyInfoFactory();
        List<Object> x509Cont = new ArrayList<>(); x509Cont.add(cert);
        X509Data x509 = kif.newX509Data(x509Cont);
        KeyInfo ki = kif.newKeyInfo(Collections.singletonList(x509));
        DOMSignContext dsc = new DOMSignContext(signingKey, doc.getDocumentElement());
        XMLSignature sig = fac.newXMLSignature(si, ki);
        sig.sign(dsc);
    }

    private boolean verifyXmlSignature(Document doc, PublicKey pub) {
        try {
            XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");
            javax.xml.xpath.XPath xp = javax.xml.xpath.XPathFactory.newInstance().newXPath();
            javax.xml.xpath.XPathExpression expr = xp.compile("/*/*[local-name()='Signature' or name()='Signature']");
            org.w3c.dom.Node sigNode = (org.w3c.dom.Node) expr.evaluate(doc, javax.xml.xpath.XPathConstants.NODE);
            if (sigNode == null) return false;
            DOMValidateContext valContext = new DOMValidateContext(pub, sigNode);
            XMLSignature sig = fac.unmarshalXMLSignature(valContext);
            return sig.validate(valContext);
        } catch (Exception e) { return false; }
    }

    private String getNodeText(org.w3c.dom.Document doc, String xpathExpr) throws Exception {
        javax.xml.xpath.XPath xp = javax.xml.xpath.XPathFactory.newInstance().newXPath();
        javax.xml.xpath.XPathExpression expr = xp.compile(xpathExpr);
        String r = (String) expr.evaluate(doc, javax.xml.xpath.XPathConstants.STRING);
        return r != null && !r.isBlank() ? r.trim() : null;
    }

    private String domToString(Document doc) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer t = tf.newTransformer();
        t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        StringWriter sw = new StringWriter();
        t.transform(new DOMSource(doc), new StreamResult(sw));
        return sw.toString();
    }

    private String uniqueRequestIdForToday() {
        long v = (System.currentTimeMillis() / 1000L) % 100_000_000L;
        return Long.toString(v);
    }
    private String escapeXml(String s) { if (s==null) return ""; return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;"); }

    private Map<String,String> fetchOtherIds(String cif) {
        Map<String,String> map = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT other_id_type, other_id_number FROM ckyc_additional_details WHERE cif = ?")) {
            ps.setString(1, cif);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getString("other_id_type"), rs.getString("other_id_number"));
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return map;
    }

    private CifRecord fetchCifRecordByCifBranchStatus(String cif, String branchCode, String cifStatus) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT cif,name,pan FROM ckyc_customer_details WHERE cif = ? AND branch_code = ? AND cif_status = ?")) {
            ps.setString(1, cif); ps.setString(2, branchCode); ps.setString(3, cifStatus);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    CifRecord r = new CifRecord(); r.setCif(rs.getString("cif")); r.setName(rs.getString("name")); r.setPan(rs.getString("pan")); return r;
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    private void writeDebugFile(String name, String content) {
        Writer w = null;
        try {
            java.nio.file.Path dir = java.nio.file.Paths.get(debugDir);
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Path p = dir.resolve(name);
            w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(p.toFile()), StandardCharsets.UTF_8));
            w.write(content);
        } catch (Exception ex) { /* ignore */ }
        finally { if (w != null) try { w.close(); } catch (Exception ignored) {} }
    }
}
