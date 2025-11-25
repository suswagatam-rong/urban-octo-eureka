package com.example.ckyc.http;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Java 8 friendly HTTP client using HttpURLConnection.
 */
public class CersaiHttpClient {

    private final String endpointUrl;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public static class Response {
        public final int status;
        public final String body;
        public Response(int status, String body) { this.status = status; this.body = body; }
    }

    public CersaiHttpClient(String endpointUrl) {
        this(endpointUrl, 10_000, 60_000);
    }

    public CersaiHttpClient(String endpointUrl, int connectTimeoutMs, int readTimeoutMs) {
        this.endpointUrl = endpointUrl;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    public Response postXml(String xmlBody) throws IOException {
        HttpURLConnection conn = null;
        OutputStream os = null;
        InputStream is = null;
        try {
            URL url = new URL(endpointUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(connectTimeoutMs);
            conn.setReadTimeout(readTimeoutMs);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/xml; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/xml");

            byte[] payload = xmlBody.getBytes(StandardCharsets.UTF_8);
            conn.setRequestProperty("Content-Length", Integer.toString(payload.length));
            os = conn.getOutputStream();
            os.write(payload);
            os.flush();

            int status = conn.getResponseCode();
            if (status >= 200 && status < 400) {
                is = conn.getInputStream();
            } else {
                is = conn.getErrorStream();
                if (is == null) is = conn.getInputStream();
            }

            String body;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                body = br.lines().collect(Collectors.joining("\n"));
            }
            return new Response(status, body);
        } finally {
            if (os != null) try { os.close(); } catch (IOException ignored) {}
            if (is != null) try { is.close(); } catch (IOException ignored) {}
            if (conn != null) conn.disconnect();
        }
    }
}
