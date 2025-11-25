package com.example.ckyc;

import com.example.ckyc.crypto.CryptoService;
import com.example.ckyc.crypto.KeyStoreKeyProvider;
import com.example.ckyc.dao.EnquiryAttemptDao;
import com.example.ckyc.http.CersaiHttpClient;
import com.example.ckyc.service.CifProcessor;

import java.sql.Connection;
import java.sql.DriverManager;

public class Main {
    public static void main(String[] args) throws Exception {
            if (args.length < 3) {
                        System.out.println("Usage:");
                                    System.out.println("  java -jar ckyc-utility.jar <FI_CODE> <BRANCH_CODE> <CIF_STATUS> [<CIF>]");
                                                System.out.println("If <CIF> omitted, all CIFs with BRANCH_CODE and CIF_STATUS are processed.");
                                                            System.exit(1);
                                                                    }

                                                                            String props = "src/main/resources/application.properties";
                                                                                    Config cfg = new Config(props);

                                                                                            // load keystore provider, crypto, http client
                                                                                                    KeyStoreKeyProvider keyProvider = new KeyStoreKeyProvider(cfg.get("keystore.path"), cfg.get("keystore.password"));
                                                                                                            CryptoService crypto = new CryptoService();
                                                                                                                    CersaiHttpClient http = new CersaiHttpClient(cfg.get("ckyc.cersai.url"));

                                                                                                                            // DB connection (ensure ojdbc driver on classpath)
                                                                                                                                    Connection conn = DriverManager.getConnection(cfg.get("db.url"), cfg.get("db.username"), cfg.get("db.password"));
                                                                                                                                            EnquiryAttemptDao dao = new EnquiryAttemptDao(conn);

                                                                                                                                                    // processor
                                                                                                                                                            CifProcessor processor = new CifProcessor(cfg, crypto, keyProvider, dao, http, conn);

                                                                                                                                                                    String fiCode = args[0];
                                                                                                                                                                            String branchCode = args[1];
                                                                                                                                                                                    String cifStatus = args[2];

                                                                                                                                                                                            if (args.length == 4) {
                                                                                                                                                                                                        String cif = args[3];
                                                                                                                                                                                                                    processor.processSingleCif(fiCode, branchCode, cifStatus, cif);
                                                                                                                                                                                                                            } else {
                                                                                                                                                                                                                                        processor.processByBranchAndStatus(fiCode, branchCode, cifStatus);
                                                                                                                                                                                                                                                }

                                                                                                                                                                                                                                                        conn.close();
                                                                                                                                                                                                                                                                System.out.println("Finished.");
                                                                                                                                                                                                                                                                    }
                                                                                                                                                                                                                                                                    }