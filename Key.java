package com.example.ckyc.crypto;

import java.io.FileInputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.PublicKey;

public class KeyStoreKeyProvider {
    private final KeyStore ks;
        private final char[] password;
            public KeyStoreKeyProvider(String path, String pwd) throws Exception {
                    this.ks = KeyStore.getInstance("PKCS12");
                            try (FileInputStream fis = new FileInputStream(path)) { ks.load(fis, pwd.toCharArray()); }
                                    this.password = pwd.toCharArray();
                                        }
                                            public PrivateKey getPrivateKey(String alias) throws Exception {
                                                    Key k = ks.getKey(alias, password);
                                                            if (!(k instanceof PrivateKey)) throw new RuntimeException("No private key for " + alias);
                                                                    return (PrivateKey) k;
                                                                        }
                                                                            public PublicKey getPublicKey(String alias) throws Exception {
                                                                                    Certificate c = ks.getCertificate(alias);
                                                                                            if (c == null) throw new RuntimeException("No certificate for " + alias);
                                                                                                    return c.getPublicKey();
                                                                                                        }
                                                                                                            public Certificate getCertificate(String alias) throws Exception {
                                                                                                                    Certificate c = ks.getCertificate(alias);
                                                                                                                            if (c == null) throw new RuntimeException("No certificate for " + alias);
                                                                                                                                    return c;
                                                                                                                                        }
                                                                                                                                        }