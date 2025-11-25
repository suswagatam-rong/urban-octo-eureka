package com.example.ckyc.http;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class CersaiHttpClient {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        private final String url;
            public CersaiHttpClient(String url) { this.url = url; }
                public HttpResponse<String> postXml(String body) throws Exception {
                        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url))
                                        .timeout(Duration.ofSeconds(60))
                                                        .header("Content-Type","application/xml")
                                                                        .POST(HttpRequest.BodyPublishers.ofString(body))
                                                                                        .build();
                                                                                                return http.send(req, HttpResponse.BodyHandlers.ofString());
                                                                                                    }
                                                                                                    } 