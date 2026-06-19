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
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class WorkspaceManagerTest {

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
    public void testIncrementalShadowCommitAndRestoration() throws IOException {
        // --- 1. First Commit: Simple Files ---
        Path file1 = workDir.resolve("hello.txt");
        Path file2 = workDir.resolve("src/main.java");
        Files.createDirectories(file2.getParent());

        Files.writeString(file1, "Hello World!");
        Files.writeString(file2, "public class main {}");

        Set<Path> changes1 = new HashSet<>();
        changes1.add(file1);
        changes1.add(file2);

        String rev1Hash = wm.scanAndCreateShadowCommit(changes1);
        assertNotNull(rev1Hash);

        // Verify index state
        FileMetadata meta1 = db.getFile("hello.txt");
        assertNotNull(meta1);
        assertEquals("BLOB", meta1.getType());
        assertEquals("hello.txt", meta1.getPath());

        FileMetadata meta2 = db.getFile("src/main.java");
        assertNotNull(meta2);
        assertEquals("BLOB", meta2.getType());

        // --- 2. Second Commit: Large Chunked File & Modification & Deletion ---
        // Modify hello.txt
        Files.writeString(file1, "Hello DraftFlow!");
        // Delete src/main.java
        Files.delete(file2);
        // Create large 1.2MB file to trigger FastCDC chunking
        Path largeFile = workDir.resolve("bigfile.bin");
        byte[] largeData = new byte[12 * 1024 * 1024 / 10]; // 1.2 MB
        new Random(42).nextBytes(largeData);
        Files.write(largeFile, largeData);

        Set<Path> changes2 = new HashSet<>();
        changes2.add(file1);
        changes2.add(file2);
        changes2.add(largeFile);

        String rev2Hash = wm.scanAndCreateShadowCommit(changes2);
        assertNotNull(rev2Hash);

        // Verify bigfile.bin is tracked as CHUNK_TREE
        FileMetadata largeMeta = db.getFile("bigfile.bin");
        assertNotNull(largeMeta);
        assertEquals("CHUNK_TREE", largeMeta.getType());

        // Verify src/main.java is removed from index
        assertNull(db.getFile("src/main.java"));

        // --- 3. Checkout Revision 1 (Revert) ---
        wm.restoreWorkingCopy(rev1Hash);

        // Check file status: hello.txt should be original, src/main.java restored, bigfile.bin deleted
        assertTrue(Files.exists(file1));
        assertEquals("Hello World!", Files.readString(file1));
        assertTrue(Files.exists(file2));
        assertEquals("public class main {}", Files.readString(file2));
        assertFalse(Files.exists(largeFile));

        // --- 4. Checkout Revision 2 (Re-apply) ---
        wm.restoreWorkingCopy(rev2Hash);

        // Check file status: hello.txt modified, src/main.java deleted, bigfile.bin restored and matches byte-for-byte
        assertTrue(Files.exists(file1));
        assertEquals("Hello DraftFlow!", Files.readString(file1));
        assertFalse(Files.exists(file2));
        assertTrue(Files.exists(largeFile));
        
        byte[] restoredLargeData = Files.readAllBytes(largeFile);
        assertArrayEquals(largeData, restoredLargeData);
    }
}
