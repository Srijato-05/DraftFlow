package com.draftflow.core;

import com.draftflow.db.FileMetadata;
import com.draftflow.db.MetadataStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AdvancedWorkspaceManagerTest {

    @TempDir
    Path tempDir;

    private CAS cas;
    private MetadataStore db;
    private WorkspaceManager wm;
    private Path workDir;

    @BeforeEach
    public void setUp() throws IOException {
        workDir = tempDir.resolve("repo");
        Files.createDirectories(workDir);

        cas = new CAS(workDir);
        cas.init();

        Path dbPath = workDir.resolve(".draftflow").resolve("index").resolve("index.mv.db");
        db = new MetadataStore(dbPath);
        db.open();
        db.setConfig("activeHead", "heads/main");

        wm = new WorkspaceManager(cas, db);
    }

    @AfterEach
    public void tearDown() {
        if (db != null) {
            db.close();
        }
    }

    @Test
    public void testFileModePreservationInDatabaseAndCAS() throws IOException {
        // Test file modes are preserved in metadata
        Path file = workDir.resolve("normal.txt");
        Files.writeString(file, "Normal file");

        // Force scan of normal file
        wm.scanAndCreateShadowCommit(Collections.singleton(file));

        FileMetadata meta = db.getFile("normal.txt");
        assertNotNull(meta);
        // On Windows it will likely be 100644, let's capture whatever it is
        int originalMode = meta.getMode();
        assertTrue(originalMode == 100644 || originalMode == 100755);

        // Let's create a custom entry in index manually with 100755 (executable mode)
        // to simulate POSIX systems since POSIX is hard to enforce on Windows runners
        String execPathStr = "bin/run.sh";
        Blob dummyBlob = new Blob("echo 'hello'".getBytes());
        String dummyHash = cas.writeObject(dummyBlob);
        FileMetadata execMeta = new FileMetadata(
            execPathStr,
            12L,
            System.currentTimeMillis(),
            dummyHash,
            ObjectType.BLOB.name(),
            100755 // Executable permission
        );
        db.putFile(execMeta);
        db.commit();

        // Scan/rebuild tree using shadow commit creation
        // By changing another file we trigger a rebuild containing both
        Files.writeString(file, "Normal file mod");
        String shadowRev = wm.scanAndCreateShadowCommit(Collections.singleton(file));

        // Read Revision and Tree
        Revision rev = (Revision) cas.readObject(shadowRev);
        Tree rootTree = (Tree) cas.readObject(rev.getTreeHash());

        // Find "bin" sub-tree
        TreeEntry binEntry = rootTree.getEntries().stream()
                .filter(e -> e.getName().equals("bin"))
                .findFirst().orElse(null);
        assertNotNull(binEntry);
        assertEquals(ObjectType.TREE, binEntry.getType());

        Tree binTree = (Tree) cas.readObject(binEntry.getHash());
        TreeEntry scriptEntry = binTree.getEntries().stream()
                .filter(e -> e.getName().equals("run.sh"))
                .findFirst().orElse(null);
        assertNotNull(scriptEntry);
        assertEquals(100755, scriptEntry.getMode());

        // Checkout the revision to see if database preserves mode
        wm.restoreWorkingCopy(shadowRev);
        FileMetadata restoredMeta = db.getFile(execPathStr);
        assertNotNull(restoredMeta);
        assertEquals(100755, restoredMeta.getMode());
    }

    @Test
    public void testFastCDCBoundaryAndDeduplication() throws IOException {
        // 1. Exactly 1MB (1024 * 1024 bytes) boundary
        Path boundaryFile = workDir.resolve("boundary.dat");
        byte[] boundaryData = new byte[1024 * 1024];
        Files.write(boundaryFile, boundaryData);

        wm.scanAndCreateShadowCommit(Collections.singleton(boundaryFile));
        FileMetadata boundaryMeta = db.getFile("boundary.dat");
        assertNotNull(boundaryMeta);
        assertEquals(ObjectType.BLOB.name(), boundaryMeta.getType()); // Exactly 1MB should be BLOB

        // 2. Exactly 1MB + 1 byte
        Path aboveBoundaryFile = workDir.resolve("above.dat");
        byte[] aboveBoundaryData = new byte[1024 * 1024 + 1];
        Files.write(aboveBoundaryFile, aboveBoundaryData);

        wm.scanAndCreateShadowCommit(Collections.singleton(aboveBoundaryFile));
        FileMetadata aboveMeta = db.getFile("above.dat");
        assertNotNull(aboveMeta);
        assertEquals(ObjectType.CHUNK_TREE.name(), aboveMeta.getType()); // 1MB + 1 byte should be CHUNK_TREE

        // 3. Repeating Data Deduplication (2MB of zeroes)
        Path repeatFile = workDir.resolve("repeat.dat");
        byte[] repeatData = new byte[2 * 1024 * 1024];
        Files.write(repeatFile, repeatData);

        // Before committing, clean existing objects to count new ones
        // Wait, CAS object count can be verified by list or tracking writeObject output.
        // Let's verify that the ChunkTree has multiple chunk hashes, but all hashes are identical!
        String shadowRev = wm.scanAndCreateShadowCommit(Collections.singleton(repeatFile));
        assertNotNull(shadowRev);
        
        FileMetadata repeatMeta = db.getFile("repeat.dat");
        assertNotNull(repeatMeta);
        assertEquals(ObjectType.CHUNK_TREE.name(), repeatMeta.getType());

        ChunkTree chunkTree = (ChunkTree) cas.readObject(repeatMeta.getHash());
        List<String> chunkHashes = chunkTree.getChunkHashes();
        assertTrue(chunkHashes.size() > 1, "Should slice 2MB into multiple chunks");

        String firstHash = chunkHashes.get(0);
        for (String hash : chunkHashes) {
            assertEquals(firstHash, hash, "All chunks of zero bytes must have the identical hash due to content-addressing");
        }
    }

    @Test
    public void testDeepNestedDirectoryTree() throws IOException {
        // Build a very deep tree
        String deepRelPath = "a/b/c/d/e/f/g/h/i/j/file.txt";
        Path deepFile = workDir.resolve(deepRelPath);
        Files.createDirectories(deepFile.getParent());
        Files.writeString(deepFile, "Deep content");

        // Scan and commit
        String shadowRev = wm.scanAndCreateShadowCommit(Collections.singleton(deepFile));
        assertNotNull(shadowRev);

        // Verify it is tracked in DB
        FileMetadata meta = db.getFile(deepRelPath);
        assertNotNull(meta);

        // Remove repo and restore
        Files.delete(deepFile);
        wm.restoreWorkingCopy(shadowRev);

        // Check if file and all parent directories are recreated
        assertTrue(Files.exists(deepFile));
        assertEquals("Deep content", Files.readString(deepFile));
    }
}
