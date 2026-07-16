package com.draftflow.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class ComprehensiveSignatureHelperTest {

    @TempDir
    Path tempDir;

    @Test
    public void testGenerateKeyPair() throws Exception {
        SignatureHelper.KeyPairStrings pair = SignatureHelper.generateKeyPair();
        assertNotNull(pair);
        assertNotNull(pair.privateKeyBase64);
        assertNotNull(pair.publicKeyBase64);
        assertFalse(pair.privateKeyBase64.isEmpty());
        assertFalse(pair.publicKeyBase64.isEmpty());
    }

    @Test
    public void testSignAndVerify() throws Exception {
        SignatureHelper.KeyPairStrings pair = SignatureHelper.generateKeyPair();
        byte[] data = "authenticate this message".getBytes(StandardCharsets.UTF_8);

        String signature = SignatureHelper.sign(data, pair.privateKeyBase64);
        assertNotNull(signature);

        // Verify with correct signature and key
        assertTrue(SignatureHelper.verify(data, signature, pair.publicKeyBase64));

        // Verify failure with modified data
        byte[] modifiedData = "authenticate that message".getBytes(StandardCharsets.UTF_8);
        assertFalse(SignatureHelper.verify(modifiedData, signature, pair.publicKeyBase64));

        // Verify failure with invalid signature format or key
        assertFalse(SignatureHelper.verify(data, "invalidsignature", pair.publicKeyBase64));
        assertFalse(SignatureHelper.verify(data, signature, "invalidpublickey"));
    }

    @Test
    public void testSignRevisionIfKeyExists() throws Exception {
        CAS cas = new CAS(tempDir);
        cas.init();

        SignatureHelper.KeyPairStrings pair = SignatureHelper.generateKeyPair();
        Path privKeyPath = cas.getDraftFlowDir().resolve("id_ecdsa");
        Path pubKeyPath = cas.getDraftFlowDir().resolve("id_ecdsa.pub");

        // Write keys to disk
        Files.writeString(privKeyPath, pair.privateKeyBase64);
        Files.writeString(pubKeyPath, pair.publicKeyBase64);

        Revision unsignedRev = new Revision(
                "treehash123",
                Collections.emptyList(),
                "change1",
                "author",
                123456L,
                "msg",
                true,
                null,
                null
        );

        Revision signedRev = SignatureHelper.signRevisionIfKeyExists(unsignedRev, cas);
        assertNotNull(signedRev.getSignature());
        assertEquals(pair.publicKeyBase64, signedRev.getPublicKey());

        // Verify signature matches
        assertTrue(SignatureHelper.verify(signedRev.getSigningData(), signedRev.getSignature(), signedRev.getPublicKey()));
    }
}
