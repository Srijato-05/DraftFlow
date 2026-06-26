package com.draftflow.core;

import com.draftflow.DraftFlow;
import com.draftflow.db.MetadataStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class AdvancedResilienceTest {

    @TempDir
    Path tempDir;

    @Test
    public void testFileLockExclusionAndRelease() throws Exception {
        CAS cas1 = new CAS(tempDir);
        cas1.init();

        CAS cas2 = new CAS(tempDir);

        // 1. Thread A acquires the lock
        assertTrue(cas1.tryAcquireLock(100), "First CAS instance should acquire lock immediately");

        // 2. Thread B attempts to acquire the lock and should time out/fail
        long start = System.currentTimeMillis();
        boolean secondAcquired = cas2.tryAcquireLock(200);
        long elapsed = System.currentTimeMillis() - start;

        assertFalse(secondAcquired, "Second CAS instance should fail to acquire lock while held");
        assertTrue(elapsed >= 200, "Second instance should block and wait for the specified timeout");

        // 3. Thread A releases the lock, Thread B should now succeed
        cas1.releaseLock();
        assertTrue(cas2.tryAcquireLock(100), "Second CAS instance should succeed after release");
        cas2.releaseLock();
    }

    @Test
    public void testCorruptedConfigAutoRecovery() throws Exception {
        CAS cas = new CAS(tempDir);
        cas.init();

        Path configPath = tempDir.resolve(".draftflow/config.json");
        assertTrue(Files.exists(configPath));

        // Write malformed JSON
        Files.writeString(configPath, "{ malformed: [ json, ");

        // Call getConfig() which should print warning, regenerate, and succeed
        DraftFlowConfig config = cas.getConfig();
        assertNotNull(config, "Config should not be null after auto-recovery");
        assertEquals("1.0", config.getVersion(), "Should use default version after recovery");
        assertTrue(config.getExclude().contains(".draftflow"), "Should contain default excludes");

        // Verify config.json is rewritten correctly
        String repairedContent = Files.readString(configPath);
        assertTrue(repairedContent.contains("SHA-256"), "Repaired config should have SHA-256");
    }

    @Test
    public void testVerifyIntegrityAndCorruptedObjectPruning() throws Exception {
        CAS cas = new CAS(tempDir);
        cas.init();

        Path dbPath = tempDir.resolve(".draftflow/index/index.mv.db");
        Files.createDirectories(dbPath.getParent());

        String healthyHash;
        String corruptHash;
        Path corruptPath;
        Path healthyPath;

        try (MetadataStore db = new MetadataStore(dbPath)) {
            db.open();
            
            // Write a healthy object
            Blob healthy = new Blob("healthy data payload".getBytes());
            healthyHash = cas.writeObject(healthy);

            // Write a corrupted object
            Blob corrupt = new Blob("to be corrupted payload".getBytes());
            corruptHash = cas.writeObject(corrupt);

            // Statically record them in the database index
            db.putFile(new com.draftflow.db.FileMetadata("file1.txt", 20, System.currentTimeMillis(), healthyHash, "BLOB", 0));
            db.putFile(new com.draftflow.db.FileMetadata("file2.txt", 23, System.currentTimeMillis(), corruptHash, "BLOB", 0));
            db.commit();

            // Corrupt the file on disk
            corruptPath = tempDir.resolve(".draftflow/objects")
                    .resolve(corruptHash.substring(0, 2))
                    .resolve(corruptHash.substring(2));
            Files.writeString(corruptPath, "CORRUPTED_PAYLOAD_DATA");

            healthyPath = tempDir.resolve(".draftflow/objects")
                    .resolve(healthyHash.substring(0, 2))
                    .resolve(healthyHash.substring(2));
        }

        // Execute the VerifyCmd directly using mock runLockedCommand
        // VerifyCmd should detect corruption, delete the corrupted file, and return failure (1) because file2.txt's CAS object is now missing from disk.
        System.setProperty("draftflow.dir", tempDir.toAbsolutePath().toString());
        DraftFlow.VerifyCmd verify = new DraftFlow.VerifyCmd();
        
        int exitCode = verify.call();
        assertEquals(1, exitCode, "Verify should return exit code 1 due to missing ref for file2.txt");

        // Verify the corrupted object file was deleted (pruned)
        assertFalse(Files.exists(corruptPath), "Corrupted object file should have been deleted by verify");
        
        // Verify healthy object file is still there
        assertTrue(Files.exists(healthyPath), "Healthy object file should remain untouched");
    }

    @Test
    public void testTransactionalRestoreFailureLeavesWorkspaceUntouched() throws Exception {
        CAS cas = new CAS(tempDir);
        cas.init();
        Path dbPath = tempDir.resolve(".draftflow/index/index.mv.db");

        try (MetadataStore db = new MetadataStore(dbPath)) {
            db.open();
            WorkspaceManager wm = new WorkspaceManager(cas, db);

            // 1. Create and save Revision 1
            Path fileA = tempDir.resolve("fileA.txt");
            Files.writeString(fileA, "Revision 1 content of A");
            wm.scanAndCreateShadowCommit(java.util.Set.of(fileA));
            String rev1 = db.getConfig("activeRevisionHash");
            assertNotNull(rev1);

            // 2. Create and save Revision 2
            Path fileB = tempDir.resolve("fileB.txt");
            Files.writeString(fileB, "Revision 2 content of B");
            Files.writeString(fileA, "Revision 2 content of A");
            wm.scanAndCreateShadowCommit(java.util.Set.of(fileA, fileB));
            String rev2 = db.getConfig("activeRevisionHash");
            assertNotNull(rev2);

            // Let's get the hash of fileA in Revision 1 to corrupt it in CAS
            Revision r1 = (Revision) cas.readObject(rev1);
            Tree tree1 = (Tree) cas.readObject(r1.getTreeHash());
            String fileAObjHash = tree1.getEntries().get(0).getHash();

            // Corrupt fileA in CAS by deleting its object file
            Path fileAInCAS = tempDir.resolve(".draftflow/objects")
                    .resolve(fileAObjHash.substring(0, 2))
                    .resolve(fileAObjHash.substring(2));
            Files.delete(fileAInCAS);

            // 3. Attempt to restore Revision 1. Since fileA is missing/corrupted in CAS, it must fail.
            assertThrows(IOException.class, () -> {
                wm.restoreWorkingCopy(rev1);
            }, "Restore should fail because fileA is missing in CAS");

            // 4. Verify workspace remains UNTOUCHED (Revision 2 files are still there and unchanged)
            assertEquals("Revision 2 content of A", Files.readString(fileA), "fileA should retain revision 2 content");
            assertEquals("Revision 2 content of B", Files.readString(fileB), "fileB should still exist");
            assertEquals(rev2, db.getConfig("activeRevisionHash"), "Database active revision should remain rev2");
        }
    }

    @Test
    public void testScannerGracefullyHandlesUnreadableFile() throws Exception {
        CAS cas = new CAS(tempDir);
        cas.init();
        Path dbPath = tempDir.resolve(".draftflow/index/index.mv.db");

        try (MetadataStore db = new MetadataStore(dbPath)) {
            db.open();
            WorkspaceManager wm = new WorkspaceManager(cas, db);

            // Create a normal file
            Path healthyFile = tempDir.resolve("healthy.txt");
            Files.writeString(healthyFile, "Healthy file content");

            // Create a file to lock exclusively (unreadable on Windows)
            Path lockedFile = tempDir.resolve("locked.txt");
            Files.writeString(lockedFile, "Locked file content");

            // Open RandomAccessFile and lock it to simulate Windows lock contention
            try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(lockedFile.toFile(), "rw");
                 java.nio.channels.FileLock lock = raf.getChannel().lock()) {
                assertNotNull(lock);
                
                // Perform scan. It should log warning for locked.txt but successfully scan healthy.txt
                String shadowHash = wm.scanAndCreateShadowCommit(java.util.Set.of(healthyFile, lockedFile));
                assertNotNull(shadowHash, "Scanner should generate a commit despite unreadable file");

                // Verify healthy file is indexed correctly
                String relStrHealthy = tempDir.relativize(healthyFile).toString().replace('\\', '/');
                assertNotNull(db.getFile(relStrHealthy), "Healthy file should be present in the index database");

                // Verify locked file was skipped/retained (or not indexed if new)
                String relStrLocked = tempDir.relativize(lockedFile).toString().replace('\\', '/');
                assertNull(db.getFile(relStrLocked), "Locked file should not be indexed as it could not be read");
            }
        }
    }
}
