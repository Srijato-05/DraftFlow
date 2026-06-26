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

import static org.junit.jupiter.api.Assertions.*;

public class AdvancedFeatureSuite2Test {

    @TempDir
    Path tempDir;

    private void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    public void testBinaryDeltaCompression() throws Exception {
        System.setProperty("draftflow.dir", tempDir.toString());
        CAS cas = new CAS(tempDir);
        cas.init();

        // 1. Create similar byte payloads
        String baseStr = "abcdefghijklmnopqrstuvwxyz0123456789\n".repeat(100);
        String targetStr = baseStr + "Additional inserted block of text\n";

        byte[] baseBytes = baseStr.getBytes(StandardCharsets.UTF_8);
        byte[] targetBytes = targetStr.getBytes(StandardCharsets.UTF_8);

        // 2. Test delta compression/decompression direct
        byte[] deltaBytes = BinaryDelta.compress(baseBytes, targetBytes);
        assertNotNull(deltaBytes);
        assertTrue(deltaBytes.length < targetBytes.length); // Must save space

        byte[] decompressedBytes = BinaryDelta.decompress(baseBytes, deltaBytes);
        assertArrayEquals(targetBytes, decompressedBytes);

        // 3. Test transparent CAS integration
        String baseHash = cas.writeObject(new Blob(baseBytes));
        String targetHash = cas.writeBlobWithDelta(targetBytes, baseHash);

        // Read metadata of target and assert it is a DELTA_BLOB
        Path objPath = tempDir.resolve(".draftflow").resolve("objects")
                .resolve(targetHash.substring(0, 2)).resolve(targetHash.substring(2));
        byte[] rawCompressed = Files.readAllBytes(objPath);
        byte[] rawDecompressed = Compressor.decompress(rawCompressed);
        String rawHeader = new String(rawDecompressed, StandardCharsets.UTF_8);
        assertTrue(rawHeader.startsWith("delta_blob "));

        // Read object back via CAS and verify it transparently returns target bytes
        DraftFlowObject readBack = cas.readObject(targetHash);
        assertTrue(readBack instanceof Blob);
        assertArrayEquals(targetBytes, ((Blob) readBack).getContent());
    }

    @Test
    public void testRebaseLinearization() throws Exception {
        System.setProperty("draftflow.dir", tempDir.toString());
        CAS cas = new CAS(tempDir);
        cas.init();

        Path dbPath = tempDir.resolve(".draftflow").resolve("index").resolve("index.mv.db");
        try (MetadataStore db = new MetadataStore(dbPath)) {
            db.open();
            db.setConfig("activeHead", "heads/main");
            db.commit();
        }

        // C1: Initial commit
        Path file1 = tempDir.resolve("file1.txt");
        Files.writeString(file1, "Line 1 in File 1\n");
        DraftFlow.SaveCmd save1 = new DraftFlow.SaveCmd();
        setField(save1, "message", "C1: Initial");
        assertEquals(0, save1.call());

        // C2: Main branch commit
        Path file2 = tempDir.resolve("file2.txt");
        Files.writeString(file2, "Line 1 in File 2\n");
        DraftFlow.SaveCmd save2 = new DraftFlow.SaveCmd();
        setField(save2, "message", "C2: On Main");
        assertEquals(0, save2.call());

        String mainHash;
        try (MetadataStore db = new MetadataStore(dbPath)) {
            db.open();
            mainHash = db.getRef("heads/main");
        }

        // Switch back to C1 to branch feature
        String c1Hash = getPermanentParent(mainHash, cas);

        DraftFlow.SwitchCmd switchCmd = new DraftFlow.SwitchCmd();
        setField(switchCmd, "revisionHash", c1Hash);
        assertEquals(0, switchCmd.call());

        // Create feature branch
        DraftFlow.BranchCmd branchCmd = new DraftFlow.BranchCmd();
        setField(branchCmd, "newBranch", "feature");
        assertEquals(0, branchCmd.call());

        // Switch to feature branch so activeHead points to heads/feature
        try (MetadataStore db = new MetadataStore(dbPath)) {
            db.open();
            db.setConfig("activeHead", "heads/feature");
            db.commit();
        }

        // C3: Feature branch commit
        Path file3 = tempDir.resolve("file3.txt");
        Files.writeString(file3, "Line 1 in File 3\n");
        DraftFlow.SaveCmd save3 = new DraftFlow.SaveCmd();
        setField(save3, "message", "C3: On Feature");
        assertEquals(0, save3.call());

        // Rebase feature onto main
        DraftFlow.RebaseCmd rebase = new DraftFlow.RebaseCmd();
        setField(rebase, "upstream", "main");
        assertEquals(0, rebase.call());

        // Verify rebased state
        try (MetadataStore db = new MetadataStore(dbPath)) {
            db.open();
            String activeHead = db.getConfig("activeHead");
            assertEquals("heads/feature", activeHead);

            String rebasedHash = db.getRef("heads/feature");
            Revision rebasedRev = (Revision) cas.readObject(getPermanentHash(rebasedHash, cas));
            assertEquals("C3: On Feature", rebasedRev.getMessage());

            // Parent of rebased C3 must be C2
            assertEquals(getPermanentHash(mainHash, cas), rebasedRev.getParentHashes().get(0));

            // Verify all files exist in workspace
            assertTrue(Files.exists(file1));
            assertTrue(Files.exists(file2));
            assertTrue(Files.exists(file3));
        }
    }

    @Test
    public void testCherryPick() throws Exception {
        System.setProperty("draftflow.dir", tempDir.toString());
        CAS cas = new CAS(tempDir);
        cas.init();

        Path dbPath = tempDir.resolve(".draftflow").resolve("index").resolve("index.mv.db");
        try (MetadataStore db = new MetadataStore(dbPath)) {
            db.open();
            db.setConfig("activeHead", "heads/main");
            db.commit();
        }

        // Commit on main branch
        Path file1 = tempDir.resolve("file1.txt");
        Files.writeString(file1, "Base File\n");
        DraftFlow.SaveCmd saveMain = new DraftFlow.SaveCmd();
        setField(saveMain, "message", "Initial Main Commit");
        assertEquals(0, saveMain.call());

        // Create branch other
        DraftFlow.BranchCmd branchOther = new DraftFlow.BranchCmd();
        setField(branchOther, "newBranch", "other");
        assertEquals(0, branchOther.call());

        // Switch to branch other
        try (MetadataStore db = new MetadataStore(dbPath)) {
            db.open();
            db.setConfig("activeHead", "heads/other");
            db.commit();
        }

        // Commit on other branch
        Path file2 = tempDir.resolve("file2.txt");
        Files.writeString(file2, "Other File Content\n");
        DraftFlow.SaveCmd saveOther = new DraftFlow.SaveCmd();
        setField(saveOther, "message", "Important change to cherry pick");
        assertEquals(0, saveOther.call());

        String otherHash;
        try (MetadataStore db = new MetadataStore(dbPath)) {
            db.open();
            otherHash = getPermanentHash(db.getRef("heads/other"), cas);
        }

        // Switch back to main branch
        DraftFlow.SwitchCmd switchCmd = new DraftFlow.SwitchCmd();
        setField(switchCmd, "revisionHash", "main");
        assertEquals(0, switchCmd.call());
        
        // Manually switch activeHead to main
        try (MetadataStore db = new MetadataStore(dbPath)) {
            db.open();
            db.setConfig("activeHead", "heads/main");
            db.commit();
        }
        
        assertFalse(Files.exists(file2)); // file2 shouldn't exist on main yet

        // Cherry pick the commit from other branch
        DraftFlow.CherryPickCmd cherry = new DraftFlow.CherryPickCmd();
        setField(cherry, "targetRevision", otherHash);
        assertEquals(0, cherry.call());

        // file2 must now be materialized on main and committed
        assertTrue(Files.exists(file2));

        try (MetadataStore db = new MetadataStore(dbPath)) {
            db.open();
            String activeRevHash = db.getConfig("activeRevisionHash");
            Revision activeRev = (Revision) cas.readObject(getPermanentHash(activeRevHash, cas));
            assertTrue(activeRev.getMessage().contains("Cherry-pick: Important change to cherry pick"));
        }
    }

    @Test
    public void testIgnoreCLI() throws Exception {
        System.setProperty("draftflow.dir", tempDir.toString());
        CAS cas = new CAS(tempDir);
        cas.init();

        // 1. Add pattern to ignore list
        DraftFlow.IgnoreCmd ignoreAdd = new DraftFlow.IgnoreCmd();
        setField(ignoreAdd, "pattern", "*.log");
        assertEquals(0, ignoreAdd.call());

        Path dfIgnoreFile = tempDir.resolve(".dfignore");
        assertTrue(Files.exists(dfIgnoreFile));
        assertTrue(Files.readString(dfIgnoreFile).contains("*.log"));

        // 2. Check path matching
        DraftFlow.IgnoreCmd ignoreCheck = new DraftFlow.IgnoreCmd();
        setField(ignoreCheck, "checkPath", "error.log");

        PrintStream oldOut = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos, true, StandardCharsets.UTF_8));
        try {
            assertEquals(0, ignoreCheck.call());
        } finally {
            System.setOut(oldOut);
        }

        String output = baos.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("Ignored: Yes"));
        assertTrue(output.contains("Source: .dfignore"));
        assertTrue(output.contains("Pattern: *.log"));
    }

    private String getPermanentHash(String hash, CAS cas) throws Exception {
        if (hash == null) return null;
        Revision r = (Revision) cas.readObject(hash);
        while (r.isDraft() && !r.getParentHashes().isEmpty()) {
            hash = r.getParentHashes().get(0);
            r = (Revision) cas.readObject(hash);
        }
        return hash;
    }

    private String getPermanentParent(String hash, CAS cas) throws Exception {
        String permHash = getPermanentHash(hash, cas);
        Revision r = (Revision) cas.readObject(permHash);
        if (r.getParentHashes().isEmpty()) return null;
        return getPermanentHash(r.getParentHashes().get(0), cas);
    }
}
