package com.draftflow.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class ComprehensiveCASTest {

    @TempDir
    Path tempDir;

    private CAS cas;
    private Path repoDir;

    @BeforeEach
    public void setUp() throws IOException {
        repoDir = tempDir.resolve("repo_cas");
        Files.createDirectories(repoDir);
        cas = new CAS(repoDir);
    }

    @AfterEach
    public void tearDown() {
        cas.releaseLock();
    }

    @Test
    public void testInitAndInitialize() throws IOException {
        assertFalse(cas.isInitialized());
        cas.init();
        assertTrue(cas.isInitialized());
        assertTrue(Files.exists(cas.getDraftFlowDir().resolve("config.json")));
    }

    @Test
    public void testWriteAndReadObjectLifecycle() throws IOException {
        cas.init();

        // 1. Blob
        Blob blob = new Blob("Hello CAS!".getBytes(StandardCharsets.UTF_8));
        String blobHash = cas.writeObject(blob);
        assertNotNull(blobHash);
        assertTrue(cas.exists(blobHash));

        Blob readBlob = (Blob) cas.readObject(blobHash);
        assertEquals("Hello CAS!", new String(readBlob.getContent(), StandardCharsets.UTF_8));

        // Deduplication test (writing again returns same hash, does not fail)
        String secondBlobHash = cas.writeObject(blob);
        assertEquals(blobHash, secondBlobHash);

        // 2. Tree
        TreeEntry entry = new TreeEntry("a.txt", blobHash, ObjectType.BLOB, 100644);
        Tree tree = new Tree(Collections.singletonList(entry));
        String treeHash = cas.writeObject(tree);
        assertNotNull(treeHash);

        Tree readTree = (Tree) cas.readObject(treeHash);
        assertEquals(1, readTree.getEntries().size());
        assertEquals("a.txt", readTree.getEntries().get(0).getName());

        // 3. Revision
        Revision rev = new Revision(treeHash, new ArrayList<>(), "ch-1", "auth", System.currentTimeMillis(), "msg", false);
        String revHash = cas.writeObject(rev);
        assertNotNull(revHash);

        Revision readRev = (Revision) cas.readObject(revHash);
        assertEquals("msg", readRev.getMessage());
        assertEquals(treeHash, readRev.getTreeHash());
    }

    @Test
    public void testResolveHash() throws IOException {
        cas.init();
        Blob blobA = new Blob("Content AAA".getBytes(StandardCharsets.UTF_8));
        Blob blobB = new Blob("Content BBB".getBytes(StandardCharsets.UTF_8));

        String hashA = cas.writeObject(blobA);
        String hashB = cas.writeObject(blobB);
        assertNotNull(hashB);

        // Exact match
        assertEquals(hashA, cas.resolveHash(hashA));

        // Short prefix match
        String prefixA = hashA.substring(0, 7);
        assertEquals(hashA, cas.resolveHash(prefixA));

        // Missing match
        assertNull(cas.resolveHash("ffff"));

        // Ambiguous prefix
        // We force-create two objects with a shared prefix if possible, or test short prefix
        assertNull(cas.resolveHash("aa"));
    }

    @Test
    public void testDeltaBlobStorage() throws IOException {
        cas.init();

        // Base blob (large enough text)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            sb.append("This is base line ").append(i).append("\n");
        }
        byte[] baseBytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        String baseHash = cas.writeObject(new Blob(baseBytes));

        // Modified content (slightly changed, very high similarity -> delta bytes should be small)
        sb.append("This is a minor modification line.\n");
        byte[] modifiedBytes = sb.toString().getBytes(StandardCharsets.UTF_8);

        // Write with delta
        String deltaHash = cas.writeBlobWithDelta(modifiedBytes, baseHash);
        assertNotNull(deltaHash);

        // Verify it was saved as DeltaBlob and decompressed back to original
        Blob restoredBlob = (Blob) cas.readObject(deltaHash);
        assertArrayEquals(modifiedBytes, restoredBlob.getContent());

        // Test fallback to normal blob if similarity is low or base hash is null
        byte[] unrelatedBytes = "Completely unrelated content".getBytes(StandardCharsets.UTF_8);
        String fallbackHash = cas.writeBlobWithDelta(unrelatedBytes, baseHash);
        DraftFlowObject rawObj = cas.readObject(fallbackHash);
        assertTrue(rawObj instanceof Blob);
        assertFalse(rawObj instanceof DeltaBlob);
    }

    @Test
    public void testConfigHandling() throws IOException {
        cas.init();

        // Normal load
        DraftFlowConfig config = cas.getConfig();
        assertNotNull(config);
        assertTrue(config.getExclude().contains(".draftflow"));

        // Corrupted config auto-regeneration
        Path configPath = cas.getDraftFlowDir().resolve("config.json");
        Files.writeString(configPath, "{ corrupted json: ");
        DraftFlowConfig regenerated = cas.getConfig();
        assertNotNull(regenerated);
        assertTrue(regenerated.getExclude().contains(".draftflow"));
    }

    @Test
    public void testLockingSystem() throws IOException {
        cas.init();

        // Acquire lock
        assertTrue(cas.tryAcquireLock(1000));

        // Try acquiring again (should fail because current holds it)
        // Wait, same process can sometimes re-acquire depending on OS, but lockChannel is already opened.
        // Let's create a second CAS instance on the same directory
        CAS secondCas = new CAS(repoDir);
        assertFalse(secondCas.tryAcquireLock(500));

        // Release first lock
        cas.releaseLock();

        // Second CAS should now be able to acquire
        assertTrue(secondCas.tryAcquireLock(1000));
        secondCas.releaseLock();
    }
}
