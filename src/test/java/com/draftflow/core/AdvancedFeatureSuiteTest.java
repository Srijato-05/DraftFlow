package com.draftflow.core;

import com.draftflow.DraftFlow;
import com.draftflow.db.MetadataStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

public class AdvancedFeatureSuiteTest {

    @TempDir
    Path tempDir;

    private void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    public void testCryptographicSigning() throws Exception {
        System.setProperty("draftflow.dir", tempDir.toString());
        CAS cas = new CAS(tempDir);
        cas.init();

        Path dbPath = tempDir.resolve(".draftflow").resolve("index").resolve("index.mv.db");
        try (MetadataStore db = new MetadataStore(dbPath)) {
            db.open();
            db.setConfig("activeHead", "heads/main");
            db.commit();
        }

        // 1. Generate keys
        DraftFlow.KeysCmd keys = new DraftFlow.KeysCmd();
        assertEquals(0, keys.call());

        Path privPath = tempDir.resolve(".draftflow").resolve("id_ecdsa");
        Path pubPath = tempDir.resolve(".draftflow").resolve("id_ecdsa.pub");
        assertTrue(Files.exists(privPath));
        assertTrue(Files.exists(pubPath));

        // 2. Save a commit, should automatically sign
        Path fileA = tempDir.resolve("fileA.txt");
        Files.writeString(fileA, "Hello World Signature Test");

        DraftFlow.SaveCmd save = new DraftFlow.SaveCmd();
        setField(save, "message", "Signed commit message");
        assertEquals(0, save.call());

        // 3. Read revision object and check signature
        try (MetadataStore db = new MetadataStore(dbPath)) {
            db.open();
            String activeRevHash = db.getConfig("activeRevisionHash");
            assertNotNull(activeRevHash);

            Revision rev = (Revision) cas.readObject(activeRevHash);
            assertNotNull(rev.getSignature());
            assertNotNull(rev.getPublicKey());

            // 4. Verify signature helper functions
            boolean verified = SignatureHelper.verify(rev.getSigningData(), rev.getSignature(), rev.getPublicKey());
            assertTrue(verified);

            // 5. Tamper with metadata to verify signature failure
            Revision tampered = new Revision(
                    rev.getTreeHash(),
                    rev.getParentHashes(),
                    rev.getChangeId(),
                    "attacker-name", // altered author
                    rev.getTimestamp(),
                    rev.getMessage(),
                    rev.isDraft(),
                    rev.getSignature(),
                    rev.getPublicKey()
            );
            boolean tamperedVerified = SignatureHelper.verify(tampered.getSigningData(), tampered.getSignature(), tampered.getPublicKey());
            assertFalse(tamperedVerified);
        }
    }

    @Test
    public void testGarbageCollection() throws Exception {
        System.setProperty("draftflow.dir", tempDir.toString());
        CAS cas = new CAS(tempDir);
        cas.init();

        Path dbPath = tempDir.resolve(".draftflow").resolve("index").resolve("index.mv.db");
        String file1Hash;
        String file2Hash;
        String perm1Hash;
        String perm2Hash;

        try (MetadataStore db = new MetadataStore(dbPath)) {
            db.open();
            db.setConfig("activeHead", "heads/main");

            // Write and commit file 1
            Path file1 = tempDir.resolve("file1.txt");
            Files.writeString(file1, "Content of File 1");
            WorkspaceManager wm = new WorkspaceManager(cas, db);
            String rev1Hash = wm.scanAndCreateShadowCommit(Collections.singleton(file1));
            
            Revision r1 = (Revision) cas.readObject(rev1Hash);
            Revision perm1 = new Revision(r1.getTreeHash(), r1.getParentHashes(), r1.getChangeId(), "A", System.currentTimeMillis(), "C1", false);
            perm1Hash = cas.writeObject(perm1);
            db.setRef("heads/main", perm1Hash);
            db.setConfig("activeRevisionHash", perm1Hash);
            db.commit();
            
            // Get files inside tree to find file1's blob hash
            Tree t1 = (Tree) cas.readObject(perm1.getTreeHash());
            file1Hash = t1.getEntries().get(0).getHash();
            assertTrue(cas.exists(file1Hash));

            // Write and commit file 2 (as a detached unreferenced commit)
            Path file2 = tempDir.resolve("file2.txt");
            Files.writeString(file2, "Content of File 2");
            String rev2Hash = wm.scanAndCreateShadowCommit(new HashSet<>(Arrays.asList(file1, file2)));
            Revision r2 = (Revision) cas.readObject(rev2Hash);
            Revision perm2 = new Revision(r2.getTreeHash(), Arrays.asList(perm1Hash), r2.getChangeId(), "A", System.currentTimeMillis(), "C2", false);
            perm2Hash = cas.writeObject(perm2);
            
            Tree t2 = (Tree) cas.readObject(perm2.getTreeHash());
            file2Hash = t2.getEntries().stream()
                    .filter(e -> e.getName().equals("file2.txt"))
                    .findFirst().get().getHash();
            assertTrue(cas.exists(file2Hash));
            assertTrue(cas.exists(perm2Hash));

            // Set activeRevisionHash and heads/main back to perm1Hash so rev2Hash (shadow of C2) is unreferenced
            db.setRef("heads/main", perm1Hash);
            db.setConfig("activeRevisionHash", perm1Hash);
            db.commit();
        }

        System.out.printf("DEBUG TEST: perm1Hash=%s, file1Hash=%s, perm2Hash=%s, file2Hash=%s\n", perm1Hash, file1Hash, perm2Hash, file2Hash);

        // Run prune while perm2 is NOT in database refs
        DraftFlow.PruneCmd prune = new DraftFlow.PruneCmd();
        assertEquals(0, prune.call());

        // Since perm2 is unreferenced, its commit and its unique blob (file2Hash) must be pruned
        assertFalse(cas.exists(perm2Hash));
        assertFalse(cas.exists(file2Hash));

        // perm1 and file1Hash are reachable from heads/main, so they must remain
        assertTrue(cas.exists(perm1Hash));
        assertTrue(cas.exists(file1Hash));
    }

    @Test
    public void testStashPushAndPop() throws Exception {
        System.setProperty("draftflow.dir", tempDir.toString());
        CAS cas = new CAS(tempDir);
        cas.init();

        Path dbPath = tempDir.resolve(".draftflow").resolve("index").resolve("index.mv.db");
        try (MetadataStore db = new MetadataStore(dbPath)) {
            db.open();
            db.setConfig("activeHead", "heads/main");
            db.commit();
        }

        // Commit initial clean file
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "Initial Line\n");

        DraftFlow.SaveCmd save = new DraftFlow.SaveCmd();
        setField(save, "message", "Initial Commit");
        assertEquals(0, save.call());

        // Modify workspace file
        Files.writeString(testFile, "Initial Line\nModified Line\n");

        // Stash working copy
        DraftFlow.StashCmd stashPush = new DraftFlow.StashCmd();
        setField(stashPush, "push", true);

        assertEquals(0, stashPush.call());

        // Working copy must be reset back to clean version
        assertEquals("Initial Line\n", Files.readString(testFile));

        // Pop stash
        DraftFlow.StashCmd stashPop = new DraftFlow.StashCmd();
        setField(stashPop, "pop", true);

        assertEquals(0, stashPop.call());

        // Modifications must be restored
        assertEquals("Initial Line\nModified Line\n", Files.readString(testFile));
    }

    @Test
    public void testLineDiffTool() throws Exception {
        System.setProperty("draftflow.dir", tempDir.toString());
        CAS cas = new CAS(tempDir);
        cas.init();

        Path dbPath = tempDir.resolve(".draftflow").resolve("index").resolve("index.mv.db");
        try (MetadataStore db = new MetadataStore(dbPath)) {
            db.open();
            db.setConfig("activeHead", "heads/main");
            db.commit();
        }

        // Commit base file
        Path testFile = tempDir.resolve("diff_test.txt");
        Files.writeString(testFile, "Line 1\nLine 2\nLine 3\n");

        DraftFlow.SaveCmd save = new DraftFlow.SaveCmd();
        setField(save, "message", "Base Commit");
        assertEquals(0, save.call());

        // Edit workspace lines
        Files.writeString(testFile, "Line 1\nLine 2 Edited\nLine 3\nLine 4 Added\n");

        // Redirect System.out to capture output
        PrintStream oldOut = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos, true, StandardCharsets.UTF_8));

        try {
            DraftFlow.DiffCmd diff = new DraftFlow.DiffCmd();
            assertEquals(0, diff.call());
        } finally {
            System.setOut(oldOut);
        }

        String output = baos.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("- Line 2"));
        assertTrue(output.contains("+ Line 2 Edited"));
        assertTrue(output.contains("+ Line 4 Added"));
    }
}
