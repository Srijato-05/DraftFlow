package com.draftflow.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class CASTest {

    @TempDir
    Path tempDir;

    private CAS cas;

    @BeforeEach
    public void setUp() throws IOException {
        cas = new CAS(tempDir);
        cas.init();
    }

    @Test
    public void testInit() {
        assertTrue(cas.isInitialized());
        assertTrue(tempDir.resolve(".draftflow").resolve("objects").toFile().exists());
    }

    @Test
    public void testBlobStorage() throws IOException {
        String testData = "Hello DraftFlow content-addressable storage!";
        Blob blob = new Blob(testData.getBytes());
        
        String hash = cas.writeObject(blob);
        assertNotNull(hash);
        assertEquals(64, hash.length()); // SHA-256 hex is 64 chars

        DraftFlowObject retrieved = cas.readObject(hash);
        assertEquals(ObjectType.BLOB, retrieved.getType());
        
        Blob retrievedBlob = (Blob) retrieved;
        assertEquals(testData, new String(retrievedBlob.getContent()));
    }

    @Test
    public void testTreeStorage() throws IOException {
        TreeEntry entry1 = new TreeEntry("file1.txt", "hash12345", ObjectType.BLOB, 100644);
        TreeEntry entry2 = new TreeEntry("src", "hash56789", ObjectType.TREE, 040000);
        
        Tree tree = new Tree(Arrays.asList(entry1, entry2));
        String hash = cas.writeObject(tree);

        DraftFlowObject retrieved = cas.readObject(hash);
        assertEquals(ObjectType.TREE, retrieved.getType());

        Tree retrievedTree = (Tree) retrieved;
        assertEquals(2, retrievedTree.getEntries().size());
        
        // Entries should be sorted by name alphabetically
        assertEquals("file1.txt", retrievedTree.getEntries().get(0).getName());
        assertEquals("src", retrievedTree.getEntries().get(1).getName());
    }

    @Test
    public void testRevisionStorage() throws IOException {
        String treeHash = Hasher.hash("dummyTreeContent");
        String changeId = UUID.randomUUID().toString();
        
        Revision revision = new Revision(
            treeHash,
            Arrays.asList("parent1Hash", "parent2Hash"),
            changeId,
            "tester",
            System.currentTimeMillis(),
            "Initial test commit",
            false
        );

        String hash = cas.writeObject(revision);

        DraftFlowObject retrieved = cas.readObject(hash);
        assertEquals(ObjectType.REVISION, retrieved.getType());

        Revision retrievedRevision = (Revision) retrieved;
        assertEquals(treeHash, retrievedRevision.getTreeHash());
        assertEquals(changeId, retrievedRevision.getChangeId());
        assertEquals("tester", retrievedRevision.getAuthor());
        assertEquals("Initial test commit", retrievedRevision.getMessage());
        assertFalse(retrievedRevision.isDraft());
        assertEquals(2, retrievedRevision.getParentHashes().size());
        assertEquals("parent1Hash", retrievedRevision.getParentHashes().get(0));
    }
}
