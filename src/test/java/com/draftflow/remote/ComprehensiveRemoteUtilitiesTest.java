package com.draftflow.remote;

import com.draftflow.core.Blob;
import com.draftflow.core.CAS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ComprehensiveRemoteUtilitiesTest {

    @TempDir
    Path tempDir;

    private CAS cas;
    private MockRemoteClient mockClient;

    @BeforeEach
    public void setUp() throws IOException {
        cas = new CAS(tempDir);
        cas.init();
        mockClient = new MockRemoteClient();
    }

    // --- Mock remote client for OCC testing ---
    private static class MockRemoteClient extends RemoteClient {
        final Map<String, String> refs = new HashMap<>();
        boolean failReadBack = false;

        public MockRemoteClient() {
            super("file://mock");
        }

        @Override
        public String getRef(String refName) {
            if (failReadBack) {
                return "interrupted_hash";
            }
            return refs.get(refName);
        }

        @Override
        public void putRef(String refName, String hash) {
            refs.put(refName, hash);
        }
    }

    @Test
    public void testOCCSuccess() throws Exception {
        // Init ref as null
        assertTrue(OCC.tryUpdateRef(mockClient, "heads/main", null, "hash1"));
        assertEquals("hash1", mockClient.getRef("heads/main"));

        // Update ref with expected old hash
        assertTrue(OCC.tryUpdateRef(mockClient, "heads/main", "hash1", "hash2"));
        assertEquals("hash2", mockClient.getRef("heads/main"));
    }

    @Test
    public void testOCCExceptions() {
        mockClient.putRef("heads/main", "hash1");

        // 1. Expected empty, but already exists
        assertThrows(OCC.ConcurrencyException.class, () -> 
                OCC.tryUpdateRef(mockClient, "heads/main", null, "hashNew")
        );

        // 2. Expected old hash mismatch
        assertThrows(OCC.ConcurrencyException.class, () -> 
                OCC.tryUpdateRef(mockClient, "heads/main", "wrongHash", "hashNew")
        );

        // 3. Verification read-back mismatch (lost update simulation)
        mockClient.failReadBack = true;
        assertThrows(OCC.ConcurrencyException.class, () -> 
                OCC.tryUpdateRef(mockClient, "heads/main", "hash1", "hashNew")
        );
    }

    @Test
    public void testPackerLifecycle() throws IOException {
        // 1. Write blobs in local CAS
        Blob blobA = new Blob("Blob content A".getBytes(StandardCharsets.UTF_8));
        Blob blobB = new Blob("Blob content B".getBytes(StandardCharsets.UTF_8));
        String hashA = cas.writeObject(blobA);
        String hashB = cas.writeObject(blobB);

        // 2. Create packfile
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Packer.createPack(Arrays.asList(hashA, hashB), cas, out);
        byte[] packBytes = out.toByteArray();
        assertTrue(packBytes.length > 0);

        // 3. Unpack in a clean remote CAS
        Path cleanRepoDir = tempDir.resolve("clean_repo");
        Files.createDirectories(cleanRepoDir);
        CAS cleanCas = new CAS(cleanRepoDir);
        cleanCas.init();

        ByteArrayInputStream in = new ByteArrayInputStream(packBytes);
        List<String> unpackedHashes = Packer.unpack(in, cleanCas);
        assertEquals(2, unpackedHashes.size());
        assertTrue(unpackedHashes.contains(hashA));
        assertTrue(unpackedHashes.contains(hashB));

        // Check objects exist in clean remote CAS
        assertTrue(cleanCas.exists(hashA));
        assertTrue(cleanCas.exists(hashB));
        Blob readA = (Blob) cleanCas.readObject(hashA);
        assertEquals("Blob content A", new String(readA.getContent(), StandardCharsets.UTF_8));
    }

    @Test
    public void testPackerFileNotFound() {
        assertThrows(FileNotFoundException.class, () -> 
                Packer.createPack(Collections.singletonList("missinghash1234567890"), cas, new ByteArrayOutputStream())
        );
    }

    @Test
    public void testPackerInvalidHeader() {
        byte[] badHeaderBytes = "BADHDR\0\0\0\0".getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream in = new ByteArrayInputStream(badHeaderBytes);
        assertThrows(IOException.class, () -> Packer.unpack(in, cas));
    }
}
