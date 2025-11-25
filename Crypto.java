package com.example.ckyc.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.OAEPParameterSpec;
import java.security.spec.PSource;
import java.util.Base64;

public class CryptoService {
    static { Security.addProvider(new BouncyCastleProvider()); }
        private static final String AES = "AES";
            private static final String AES_GCM = "AES/GCM/NoPadding";
                private static final int GCM_IV_LEN = 12;
                    private static final int GCM_TAG_BITS = 128;
                        private static final String RSA_OAEP = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
                            private static final String SIGN_ALGO = "SHA256withRSA";
                                private final SecureRandom rnd = new SecureRandom();

                                    public SecretKey generateSessionKey() throws Exception {
                                            KeyGenerator kg = KeyGenerator.getInstance(AES);
                                                    kg.init(256, rnd);
                                                            return kg.generateKey();
                                                                }
                                                                    public byte[] aesGcmEncrypt(byte[] plain, SecretKey key, byte[] aad) throws Exception {
                                                                            byte[] iv = new byte[GCM_IV_LEN];
                                                                                    rnd.nextBytes(iv);
                                                                                            Cipher c = Cipher.getInstance(AES_GCM);
                                                                                                    GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, iv);
                                                                                                            c.init(Cipher.ENCRYPT_MODE, key, spec);
                                                                                                                    if (aad != null) c.updateAAD(aad);
                                                                                                                            byte[] ct = c.doFinal(plain);
                                                                                                                                    return ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array();
                                                                                                                                        }
                                                                                                                                            public byte[] aesGcmDecrypt(byte[] ivAndCipher, SecretKey key, byte[] aad) throws Exception {
                                                                                                                                                    byte[] iv = new byte[GCM_IV_LEN];
                                                                                                                                                            System.arraycopy(ivAndCipher, 0, iv, 0, iv.length);
                                                                                                                                                                    byte[] ct = new byte[ivAndCipher.length - iv.length];
                                                                                                                                                                            System.arraycopy(ivAndCipher, iv.length, ct, 0, ct.length);
                                                                                                                                                                                    Cipher c = Cipher.getInstance(AES_GCM);
                                                                                                                                                                                            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
                                                                                                                                                                                                    if (aad != null) c.updateAAD(aad);
                                                                                                                                                                                                            return c.doFinal(ct);
                                                                                                                                                                                                                }
                                                                                                                                                                                                                    public byte[] wrapKeyWithRsa(SecretKey aesKey, PublicKey peerPublic) throws Exception {
                                                                                                                                                                                                                            Cipher cipher = Cipher.getInstance(RSA_OAEP);
                                                                                                                                                                                                                                    OAEPParameterSpec oaep = new OAEPParameterSpec("SHA-256","MGF1", new MGF1ParameterSpec("SHA-256"), PSource.PSpecified.DEFAULT);
                                                                                                                                                                                                                                            cipher.init(Cipher.ENCRYPT_MODE, peerPublic, oaep);
                                                                                                                                                                                                                                                    return cipher.doFinal(aesKey.getEncoded());
                                                                                                                                                                                                                                                        }
                                                                                                                                                                                                                                                            public SecretKey unwrapKeyWithRsa(byte[] wrapped, PrivateKey ourPrivate) throws Exception {
                                                                                                                                                                                                                                                                    Cipher cipher = Cipher.getInstance(RSA_OAEP);
                                                                                                                                                                                                                                                                            OAEPParameterSpec oaep = new OAEPParameterSpec("SHA-256","MGF1", new MGF1ParameterSpec("SHA-256"), PSource.PSpecified.DEFAULT);
                                                                                                                                                                                                                                                                                    cipher.init(Cipher.DECRYPT_MODE, ourPrivate, oaep);
                                                                                                                                                                                                                                                                                            byte[] keyBytes = cipher.doFinal(wrapped);
                                                                                                                                                                                                                                                                                                    return new SecretKeySpec(keyBytes, AES);
                                                                                                                                                                                                                                                                                                        }
                                                                                                                                                                                                                                                                                                            public byte[] sign(byte[] data, PrivateKey pk) throws Exception {
                                                                                                                                                                                                                                                                                                                    Signature s = Signature.getInstance(SIGN_ALGO);
                                                                                                                                                                                                                                                                                                                            s.initSign(pk);
                                                                                                                                                                                                                                                                                                                                    s.update(data);
                                                                                                                                                                                                                                                                                                                                            return s.sign();
                                                                                                                                                                                                                                                                                                                                                }
                                                                                                                                                                                                                                                                                                                                                    public boolean verify(byte[] data, byte[] sig, PublicKey pub) throws Exception {
                                                                                                                                                                                                                                                                                                                                                            Signature s = Signature.getInstance(SIGN_ALGO);
                                                                                                                                                                                                                                                                                                                                                                    s.initVerify(pub);
                                                                                                                                                                                                                                                                                                                                                                            s.update(data);
                                                                                                                                                                                                                                                                                                                                                                                    return s.verify(sig);
                                                                                                                                                                                                                                                                                                                                                                                        }
                                                                                                                                                                                                                                                                                                                                                                                            public static String b64(byte[] b){ return Base64.getEncoder().encodeToString(b); }
                                                                                                                                                                                                                                                                                                                                                                                                public static byte[] b64d(String s){ return Base64.getDecoder().decode(s); }
                                                                                                                                                                                                                                                                                                                                                                                                }