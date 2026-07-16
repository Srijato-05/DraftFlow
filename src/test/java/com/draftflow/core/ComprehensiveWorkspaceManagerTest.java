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
import java.util.ArrayList;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class ComprehensiveWorkspaceManagerTest {

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
    public void testApplyRevisionDiffAddedFile() throws Exception {
        // 1. Create base commit tree (empty)
        String baseTree = wm.rebuildTree(Collections.emptyList());
        Revision baseRev = new Revision(baseTree, new ArrayList<>(), "change-1", "author", System.currentTimeMillis(), "base", false);
        String baseHash = cas.writeObject(baseRev);

        // 2. Create target commit tree (with a.txt added)
        String blobHash = cas.writeObject(new Blob("content A".getBytes(StandardCharsets.UTF_8)));
        FileMetadata meta = new FileMetadata("a.txt", 9L, System.currentTimeMillis(), blobHash, ObjectType.BLOB.name(), 100644);
        String targetTree = wm.rebuildTree(Collections.singletonList(meta));
        Revision targetRev = new Revision(targetTree, new ArrayList<>(), "change-2", "author", System.currentTimeMillis(), "target", false);
        String targetHash = cas.writeObject(targetRev);

        // 3. Apply diff to workspace
        wm.applyRevisionDiff(baseHash, targetHash);

        Path addedFile = workDir.resolve("a.txt");
        assertTrue(Files.exists(addedFile));
        assertEquals("content A", Files.readString(addedFile));
    }

    @Test
    public void testApplyRevisionDiffDeletedFile() throws Exception {
        // 1. Create base commit tree (with a.txt)
        String blobHash = cas.writeObject(new Blob("content A".getBytes(StandardCharsets.UTF_8)));
        FileMetadata meta = new FileMetadata("a.txt", 9L, System.currentTimeMillis(), blobHash, ObjectType.BLOB.name(), 100644);
        String baseTree = wm.rebuildTree(Collections.singletonList(meta));
        Revision baseRev = new Revision(baseTree, new ArrayList<>(), "change-1", "author", System.currentTimeMillis(), "base", false);
        String baseHash = cas.writeObject(baseRev);

        // 2. Create target commit tree (empty, a.txt deleted)
        String targetTree = wm.rebuildTree(Collections.emptyList());
        Revision targetRev = new Revision(targetTree, new ArrayList<>(), "change-2", "author", System.currentTimeMillis(), "target", false);
        String targetHash = cas.writeObject(targetRev);

        // Put a.txt in workspace and index
        Path file = workDir.resolve("a.txt");
        Files.writeString(file, "content A");
        db.putFile(meta);

        // 3. Apply diff (should delete file)
        wm.applyRevisionDiff(baseHash, targetHash);

        assertFalse(Files.exists(file));
        assertNull(db.getFile("a.txt"));
    }

    @Test
    public void testApplyRevisionDiffConflictAndCleanMerge() throws Exception {
        // 1. Create base commit tree
        String baseBlob = cas.writeObject(new Blob("line1\nline2\nline3\n".getBytes(StandardCharsets.UTF_8)));
        FileMetadata baseMeta = new FileMetadata("a.txt", 18L, System.currentTimeMillis(), baseBlob, ObjectType.BLOB.name(), 100644);
        String baseTree = wm.rebuildTree(Collections.singletonList(baseMeta));
        Revision baseRev = new Revision(baseTree, new ArrayList<>(), "change-1", "author", System.currentTimeMillis(), "base", false);
        String baseHash = cas.writeObject(baseRev);

        // 2. Create target commit tree (modified line3)
        String targetBlob = cas.writeObject(new Blob("line1\nline2\nline3 target\n".getBytes(StandardCharsets.UTF_8)));
        FileMetadata targetMeta = new FileMetadata("a.txt", 25L, System.currentTimeMillis(), targetBlob, ObjectType.BLOB.name(), 100644);
        String targetTree = wm.rebuildTree(Collections.singletonList(targetMeta));
        Revision targetRev = new Revision(targetTree, new ArrayList<>(), "change-2", "author", System.currentTimeMillis(), "target", false);
        String targetHash = cas.writeObject(targetRev);

        // Case A: Clean Merge (disk has modified line1, target has modified line3)
        Path file = workDir.resolve("a.txt");
        Files.writeString(file, "line1 disk\nline2\nline3\n");
        db.putFile(baseMeta);

        wm.applyRevisionDiff(baseHash, targetHash);

        assertTrue(Files.exists(file));
        String merged = Files.readString(file);
        assertTrue(merged.contains("line1 disk"));
        assertTrue(merged.contains("line3 target"));

        // Case B: Conflicting Merge (disk modified line3 differently to target)
        // Reset base state
        Files.writeString(file, "line1\nline2\nline3 disk conflict\n");
        db.putFile(baseMeta);

        wm.applyRevisionDiff(baseHash, targetHash);

        String conflictContent = Files.readString(file);
        assertTrue(conflictContent.contains("<<<<<<< OURS"));
        assertTrue(conflictContent.contains("line3 disk conflict"));
        assertTrue(conflictContent.contains("======="));
        assertTrue(conflictContent.contains("line3 target"));
        assertTrue(conflictContent.contains(">>>>>>> THEIRS"));
    }
}
