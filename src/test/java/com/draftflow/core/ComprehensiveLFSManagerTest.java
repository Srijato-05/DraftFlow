package com.draftflow.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class ComprehensiveLFSManagerTest {

    @TempDir
    Path tempDir;

    @Test
    public void testIsLfsFileByThreshold() throws Exception {
        Path file = tempDir.resolve("large.bin");
        Files.write(file, new byte[100]); // 100 bytes

        DraftFlowConfig config = new DraftFlowConfig();
        // Threshold is 50 bytes
        config.setLfsSizeThreshold(50L);
        config.setLfsExtensions(Collections.emptyList());

        assertTrue(LFSManager.isLfsFile(file, config));

        // Threshold is 150 bytes
        config.setLfsSizeThreshold(150L);
        assertFalse(LFSManager.isLfsFile(file, config));
    }

    @Test
    public void testIsLfsFileByExtension() throws Exception {
        Path file = tempDir.resolve("image.png");
        Files.write(file, new byte[10]); // 10 bytes

        DraftFlowConfig config = new DraftFlowConfig();
        config.setLfsSizeThreshold(100L); // threshold is larger than file
        config.setLfsExtensions(Arrays.asList(".png", ".mp4"));

        assertTrue(LFSManager.isLfsFile(file, config));

        Path otherFile = tempDir.resolve("doc.txt");
        Files.write(otherFile, new byte[10]);
        assertFalse(LFSManager.isLfsFile(otherFile, config));
    }

    @Test
    public void testCreateAndParsePointer() throws Exception {
        Path file = tempDir.resolve("source.bin");
        String content = "Large data content";
        Files.writeString(file, content);

        String pointer = LFSManager.createLfsPointer(tempDir, file);
        assertTrue(pointer.contains("version draftflow-lfs/v1"));
        assertTrue(pointer.contains("oid sha256:"));

        LFSManager.LfsPointer parsed = LFSManager.parsePointer(pointer);
        assertNotNull(parsed);
        assertEquals(content.getBytes(StandardCharsets.UTF_8).length, parsed.size);
        assertEquals(Hasher.hash(content.getBytes(StandardCharsets.UTF_8)), parsed.oid);
    }

    @Test
    public void testParsePointerInvalid() {
        assertNull(LFSManager.parsePointer(null));
        assertNull(LFSManager.parsePointer("invalid format"));
        assertNull(LFSManager.parsePointer("version draftflow-lfs/v2\noid sha256:123\nsize 10\n"));
        assertNull(LFSManager.parsePointer("version draftflow-lfs/v1\nsize 10\n")); // missing oid
        assertNull(LFSManager.parsePointer("version draftflow-lfs/v1\noid sha256:123\n")); // missing size
        assertNull(LFSManager.parsePointer("version draftflow-lfs/v1\noid sha256:123\nsize abc\n")); // invalid size format
    }

    @Test
    public void testRestoreLfsFile() throws Exception {
        Path file = tempDir.resolve("source.bin");
        String content = "Restore test content";
        Files.writeString(file, content);

        String pointerContent = LFSManager.createLfsPointer(tempDir, file);
        LFSManager.LfsPointer ptr = LFSManager.parsePointer(pointerContent);

        Path destFile = tempDir.resolve("subdir").resolve("restored.bin");
        LFSManager.restoreLfsFile(tempDir, ptr, destFile);

        assertTrue(Files.exists(destFile));
        assertEquals(content, Files.readString(destFile));
    }

    @Test
    public void testRestoreLfsFileMissing() {
        LFSManager.LfsPointer ptr = new LFSManager.LfsPointer("nonexistentoid1234567890", 100L);
        Path destFile = tempDir.resolve("failed_restore.bin");

        assertThrows(IOException.class, () -> {
            LFSManager.restoreLfsFile(tempDir, ptr, destFile);
        });
    }

    @Test
    public void testPushAndPullLfsObject() throws Exception {
        Path file = tempDir.resolve("source.bin");
        String content = "Sync test content";
        Files.writeString(file, content);

        String pointerContent = LFSManager.createLfsPointer(tempDir, file);
        LFSManager.LfsPointer ptr = LFSManager.parsePointer(pointerContent);

        Path remoteDir = tempDir.resolve("remote_repo");
        LFSManager.pushLfsObject(tempDir, ptr.oid, remoteDir);

        Path remoteLfsPath = remoteDir.resolve("lfs").resolve(ptr.oid.substring(0, 2)).resolve(ptr.oid.substring(2));
        assertTrue(Files.exists(remoteLfsPath));
        assertEquals(content, Files.readString(remoteLfsPath));

        // Pull back to a new repo root
        Path newRepo = tempDir.resolve("new_local_repo");
        LFSManager.pullLfsObject(newRepo, ptr.oid, remoteDir);

        Path pulledPath = newRepo.resolve(".draftflow").resolve("lfs").resolve(ptr.oid.substring(0, 2)).resolve(ptr.oid.substring(2));
        assertTrue(Files.exists(pulledPath));
        assertEquals(content, Files.readString(pulledPath));
    }
}
