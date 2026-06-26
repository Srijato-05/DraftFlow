package com.draftflow.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class SignatureHelper {

    public static class KeyPairStrings {
        public final String privateKeyBase64;
        public final String publicKeyBase64;

        public KeyPairStrings(String privateKeyBase64, String publicKeyBase64) {
            this.privateKeyBase64 = privateKeyBase64;
            this.publicKeyBase64 = publicKeyBase64;
        }
    }

    public static KeyPairStrings generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
        keyGen.initialize(256); // secp256r1 is default for EC 256 in most JDKs
        KeyPair pair = keyGen.generateKeyPair();
        String priv = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
        String pub = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        return new KeyPairStrings(priv, pub);
    }

    public static String sign(byte[] data, String privateKeyBase64) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(privateKeyBase64.trim());
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("EC");
        PrivateKey privateKey = kf.generatePrivate(spec);

        Signature dsa = Signature.getInstance("SHA256withECDSA");
        dsa.initSign(privateKey);
        dsa.update(data);
        return Base64.getEncoder().encodeToString(dsa.sign());
    }

    public static boolean verify(byte[] data, String signatureBase64, String publicKeyBase64) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(publicKeyBase64.trim());
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("EC");
            PublicKey publicKey = kf.generatePublic(spec);

            Signature dsa = Signature.getInstance("SHA256withECDSA");
            dsa.initVerify(publicKey);
            dsa.update(data);
            byte[] sigBytes = Base64.getDecoder().decode(signatureBase64.trim());
            return dsa.verify(sigBytes);
        } catch (Exception e) {
            return false;
        }
    }

    public static Revision signRevisionIfKeyExists(Revision rev, CAS cas) {
        Path privKeyPath = cas.getDraftFlowDir().resolve("id_ecdsa");
        Path pubKeyPath = cas.getDraftFlowDir().resolve("id_ecdsa.pub");
        if (Files.exists(privKeyPath) && Files.exists(pubKeyPath)) {
            try {
                String priv = Files.readString(privKeyPath, java.nio.charset.StandardCharsets.UTF_8).trim();
                String pub = Files.readString(pubKeyPath, java.nio.charset.StandardCharsets.UTF_8).trim();
                String signature = sign(rev.getSigningData(), priv);
                return new Revision(
                        rev.getTreeHash(),
                        rev.getParentHashes(),
                        rev.getChangeId(),
                        rev.getAuthor(),
                        rev.getTimestamp(),
                        rev.getMessage(),
                        rev.isDraft(),
                        signature,
                        pub
                );
            } catch (Exception e) {
                System.err.println("Warning: Failed to sign revision: " + e.getMessage());
            }
        }
        return rev;
    }
}
