package com.draftflow.diff;

import com.draftflow.core.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TreeDifferTest {

    @TempDir
    Path tempDir;

    private CAS cas;

    @BeforeEach
    public void setUp() throws IOException {
        cas = new CAS(tempDir);
        cas.init();
    }

    @Test
    public void testTreeDiff() throws IOException {
        // --- 1. Create Initial Tree State ---
        // Blobs
        String blobMainHash = cas.writeObject(new Blob("public class Main {}".getBytes()));
        String blobUtilHash = cas.writeObject(new Blob("public class Util {}".getBytes()));
        String dbConnHash = cas.writeObject(new Blob("database connection pool".getBytes()));

        // Db Subtree
        TreeEntry dbConnEntry = new TreeEntry("Connection.java", dbConnHash, ObjectType.BLOB, 100644);
        String dbTreeHash = cas.writeObject(new Tree(Arrays.asList(dbConnEntry)));

        // Root Tree Entry list
        TreeEntry mainEntry = new TreeEntry("Main.java", blobMainHash, ObjectType.BLOB, 100644);
        TreeEntry utilEntry = new TreeEntry("Util.java", blobUtilHash, ObjectType.BLOB, 100644);
        TreeEntry dbEntry = new TreeEntry("db", dbTreeHash, ObjectType.TREE, 040000);

        String initialTreeHash = cas.writeObject(new Tree(Arrays.asList(mainEntry, utilEntry, dbEntry)));

        // --- 2. Create Modified Tree State ---
        // Main.java modified
        String blobMainModHash = cas.writeObject(new Blob("public class Main { /* modified */ }".getBytes()));
        // Util.java will be deleted (omitted from root entry list)
        // config.json added to root
        String blobConfigHash = cas.writeObject(new Blob("{\"port\": 8080}".getBytes()));
        // db/Connection.java will remain
        // db/Schema.java added to db subtree
        String dbSchemaHash = cas.writeObject(new Blob("CREATE TABLE users;".getBytes()));

        // Modified Db Subtree
        TreeEntry dbConnEntry2 = new TreeEntry("Connection.java", dbConnHash, ObjectType.BLOB, 100644);
        TreeEntry dbSchemaEntry = new TreeEntry("Schema.java", dbSchemaHash, ObjectType.BLOB, 100644);
        String dbTreeModHash = cas.writeObject(new Tree(Arrays.asList(dbConnEntry2, dbSchemaEntry)));

        // Root Tree Entry list (Modified)
        TreeEntry mainModEntry = new TreeEntry("Main.java", blobMainModHash, ObjectType.BLOB, 100644);
        TreeEntry configEntry = new TreeEntry("config.json", blobConfigHash, ObjectType.BLOB, 100644);
        TreeEntry dbModEntry = new TreeEntry("db", dbTreeModHash, ObjectType.TREE, 040000);

        String modifiedTreeHash = cas.writeObject(new Tree(Arrays.asList(mainModEntry, configEntry, dbModEntry)));

        // --- 3. Diff and Verify ---
        List<FileDiff> diffs = TreeDiffer.diff(initialTreeHash, modifiedTreeHash, cas);

        // We expect 4 diffs:
        // - Main.java (MODIFIED)
        // - Util.java (DELETED)
        // - config.json (ADDED)
        // - db/Schema.java (ADDED)
        assertEquals(4, diffs.size());

        // Since it's sorted alphabetically:
        // 0: Main.java
        // 1: Util.java
        // 2: config.json
        // 3: db/Schema.java
        
        FileDiff diff0 = diffs.get(0);
        assertEquals("Main.java", diff0.getPath());
        assertEquals(DiffType.MODIFIED, diff0.getType());
        assertEquals(blobMainHash, diff0.getOldHash());
        assertEquals(blobMainModHash, diff0.getNewHash());

        FileDiff diff1 = diffs.get(1);
        assertEquals("Util.java", diff1.getPath());
        assertEquals(DiffType.DELETED, diff1.getType());
        assertEquals(blobUtilHash, diff1.getOldHash());
        assertNull(diff1.getNewHash());

        FileDiff diff2 = diffs.get(2);
        assertEquals("config.json", diff2.getPath());
        assertEquals(DiffType.ADDED, diff2.getType());
        assertNull(diff2.getOldHash());
        assertEquals(blobConfigHash, diff2.getNewHash());

        FileDiff diff3 = diffs.get(3);
        assertEquals("db/Schema.java", diff3.getPath());
        assertEquals(DiffType.ADDED, diff3.getType());
        assertNull(diff3.getOldHash());
        assertEquals(dbSchemaHash, diff3.getNewHash());
    }

    @Test
    public void testEmptyDiff() throws IOException {
        String blobHash = cas.writeObject(new Blob("same same".getBytes()));
        TreeEntry entry = new TreeEntry("file.txt", blobHash, ObjectType.BLOB, 100644);
        String treeHash = cas.writeObject(new Tree(Arrays.asList(entry)));

        List<FileDiff> diffs = TreeDiffer.diff(treeHash, treeHash, cas);
        assertTrue(diffs.isEmpty());
    }
}
