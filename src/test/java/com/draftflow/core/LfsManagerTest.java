package com.draftflow.core;

import com.draftflow.db.FileMetadata;
import com.draftflow.db.MetadataStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class LfsManagerTest {

    @TempDir
    Path tempDir;

    private CAS cas;
    private MetadataStore db;
    private WorkspaceManager wm;
    private Path workDir;
    private DraftFlowConfig config;

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
        config = new DraftFlowConfig();
    }

    @AfterEach
    public void tearDown() {
        if (db != null) {
            db.close();
        }
    }

    @Test
    public void testIsLfsFile() throws IOException {
        Path imageFile = workDir.resolve("pic.png");
        Files.writeString(imageFile, "some dummy png content");

        Path normalFile = workDir.resolve("doc.txt");
        Files.writeString(normalFile, "short text");

        // png is a default LFS extension
        assertTrue(LFSManager.isLfsFile(imageFile, config));
        // txt is not
        assertFalse(LFSManager.isLfsFile(normalFile, config));

        // Let's check size threshold
        Path bigTxtFile = workDir.resolve("huge.txt");
        // Create 11MB file (default threshold is 10MB)
        byte[] bigData = new byte[11 * 1024 * 1024];
        Files.write(bigTxtFile, bigData);
        assertTrue(LFSManager.isLfsFile(bigTxtFile, config));
    }

    @Test
    public void testCreateAndParsePointer() throws IOException {
        Path largeFile = workDir.resolve("archive.zip");
        String content = "Zip archive content representation";
        Files.writeString(largeFile, content);

        String pointer = LFSManager.createLfsPointer(workDir, largeFile);
        assertNotNull(pointer);
        assertTrue(pointer.contains("version draftflow-lfs/v1"));
        assertTrue(pointer.contains("size " + content.getBytes(StandardCharsets.UTF_8).length));

        LFSManager.LfsPointer parsed = LFSManager.parsePointer(pointer);
        assertNotNull(parsed);
        assertEquals(content.getBytes(StandardCharsets.UTF_8).length, parsed.size);
        assertNotNull(parsed.oid);

        // Verify the LFS cache contains the file
        Path cacheFile = workDir.resolve(".draftflow").resolve("lfs")
                .resolve(parsed.oid.substring(0, 2)).resolve(parsed.oid.substring(2));
        assertTrue(Files.exists(cacheFile));
        assertEquals(content, Files.readString(cacheFile));

        // Test malformed parses
        assertNull(LFSManager.parsePointer(null));
        assertNull(LFSManager.parsePointer("invalid format content"));
        assertNull(LFSManager.parsePointer("version draftflow-lfs/v1\nsize invalid\noid sha256:abc"));
    }

    @Test
    public void testRestoreLfsFile() throws IOException {
        Path original = workDir.resolve("video.mp4");
        String content = "fake mp4 data";
        Files.writeString(original, content);

        String pointer = LFSManager.createLfsPointer(workDir, original);
        LFSManager.LfsPointer ptr = LFSManager.parsePointer(pointer);

        // Delete original and restore it
        Files.delete(original);
        assertFalse(Files.exists(original));

        LFSManager.restoreLfsFile(workDir, ptr, original);
        assertTrue(Files.exists(original));
        assertEquals(content, Files.readString(original));

        // Verify exception if object missing in cache
        LFSManager.LfsPointer missingPtr = new LFSManager.LfsPointer("deadbeefmissingoid", 100);
        assertThrows(IOException.class, () -> LFSManager.restoreLfsFile(workDir, missingPtr, original));
    }

    @Test
    public void testPushAndPullLfsObject() throws IOException {
        Path original = workDir.resolve("image.png");
        String content = "png bytes";
        Files.writeString(original, content);

        String pointer = LFSManager.createLfsPointer(workDir, original);
        LFSManager.LfsPointer ptr = LFSManager.parsePointer(pointer);

        Path remoteDir = tempDir.resolve("remote");
        Files.createDirectories(remoteDir);

        // Push
        LFSManager.pushLfsObject(workDir, ptr.oid, remoteDir);
        Path remoteLfsFile = remoteDir.resolve("lfs").resolve(ptr.oid.substring(0, 2)).resolve(ptr.oid.substring(2));
        assertTrue(Files.exists(remoteLfsFile));
        assertEquals(content, Files.readString(remoteLfsFile));

        // Delete local cache
        Path cacheFile = workDir.resolve(".draftflow").resolve("lfs")
                .resolve(ptr.oid.substring(0, 2)).resolve(ptr.oid.substring(2));
        Files.delete(cacheFile);
        assertFalse(Files.exists(cacheFile));

        // Pull
        LFSManager.pullLfsObject(workDir, ptr.oid, remoteDir);
        assertTrue(Files.exists(cacheFile));
        assertEquals(content, Files.readString(cacheFile));
    }

    @Test
    public void testWorkspaceManagerIntegration() throws IOException {
        Path largeFile = workDir.resolve("data.zip");
        String content = "This is a large data zip file";
        Files.writeString(largeFile, content);

        // Scan and commit
        String revHash = wm.scanAndCreateShadowCommit(Collections.singleton(largeFile));
        assertNotNull(revHash);

        // In DB, metadata path is relative: "data.zip"
        FileMetadata meta = db.getFile("data.zip");
        assertNotNull(meta);
        assertEquals(ObjectType.BLOB.name(), meta.getType());

        // Read the actual object written to CAS (it must be the LFS pointer file, not the zip contents)
        Blob casBlob = (Blob) cas.readObject(meta.getHash());
        byte[] casBytes = casBlob.getContent();
        String casStr = new String(casBytes, StandardCharsets.UTF_8);
        assertTrue(casStr.contains("version draftflow-lfs/v1"));

        // Delete the original workspace file
        Files.delete(largeFile);
        assertFalse(Files.exists(largeFile));

        // Restore working copy (transparent restoration)
        wm.restoreWorkingCopy(revHash);
        assertTrue(Files.exists(largeFile));
        assertEquals(content, Files.readString(largeFile));
    }
}
