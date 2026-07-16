package com.draftflow.core;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class ComprehensiveCoreModelsTest {

    @Test
    public void testHasher() {
        String testStr = "DraftFlowVCS";
        String hash1 = Hasher.hash(testStr);
        assertNotNull(hash1);
        assertEquals(64, hash1.length()); // SHA-256 hex string length

        String hash2 = Hasher.hash(testStr.getBytes(StandardCharsets.UTF_8));
        assertEquals(hash1, hash2);

        // Verify NoSuchAlgorithmException wraps into RuntimeException
        assertThrows(RuntimeException.class, () -> Hasher.hash(testStr.getBytes(StandardCharsets.UTF_8), "UNKNOWN-ALGO"));
    }

    @Test
    public void testRevision() {
        long now = System.currentTimeMillis();
        Revision rev = new Revision(
                "treehash123",
                Arrays.asList("parent1", "parent2"),
                "change-id-custom",
                "author-name",
                now,
                "commit message",
                true,
                "sig-base64",
                "pub-base64"
        );

        assertEquals("treehash123", rev.getTreeHash());
        assertEquals(2, rev.getParentHashes().size());
        assertEquals("change-id-custom", rev.getChangeId());
        assertEquals("author-name", rev.getAuthor());
        assertEquals(now, rev.getTimestamp());
        assertEquals("commit message", rev.getMessage());
        assertTrue(rev.isDraft());
        assertEquals("sig-base64", rev.getSignature());
        assertEquals("pub-base64", rev.getPublicKey());
        assertEquals(ObjectType.REVISION, rev.getType());

        // Test Serialization and Deserialization
        byte[] serialized = rev.serialize();
        assertNotNull(serialized);

        Revision deserialized = Revision.deserialize(serialized);
        assertEquals(rev.getTreeHash(), deserialized.getTreeHash());
        assertEquals(rev.getChangeId(), deserialized.getChangeId());
        assertEquals(rev.getAuthor(), deserialized.getAuthor());
        assertEquals(rev.getTimestamp(), deserialized.getTimestamp());
        assertEquals(rev.isDraft(), deserialized.isDraft());
        assertEquals(rev.getSignature(), deserialized.getSignature());
        assertEquals(rev.getPublicKey(), deserialized.getPublicKey());

        // Test getSigningData content
        byte[] signingData = rev.getSigningData();
        assertNotNull(signingData);
        String signingStr = new String(signingData, StandardCharsets.UTF_8);
        assertTrue(signingStr.contains("treehash123"));
        assertTrue(signingStr.contains("parent1,parent2"));
        assertTrue(signingStr.contains("change-id-custom"));
        assertTrue(signingStr.contains("author-name"));
        assertTrue(signingStr.contains("commit message"));
        assertTrue(signingStr.contains("true"));
    }

    @Test
    public void testBlob() {
        byte[] content = "blob content".getBytes(StandardCharsets.UTF_8);
        Blob blob = new Blob(content);
        assertArrayEquals(content, blob.getContent());
        assertEquals(ObjectType.BLOB, blob.getType());
        assertArrayEquals(content, blob.serialize());

        // Test null safety
        Blob nullBlob = new Blob(null);
        assertEquals(0, nullBlob.getContent().length);
    }

    @Test
    public void testTreeAndTreeEntry() {
        TreeEntry entryB = new TreeEntry("b.txt", "hashB", ObjectType.BLOB, 100644);
        TreeEntry entryA = new TreeEntry("a.txt", "hashA", ObjectType.BLOB, 100644);
        TreeEntry entryDir = new TreeEntry("subdir", "hashSub", ObjectType.TREE, 040000);

        // Sorting check (a.txt should come before b.txt)
        Tree tree = new Tree(Arrays.asList(entryB, entryA, entryDir));
        assertEquals(3, tree.getEntries().size());
        assertEquals("a.txt", tree.getEntries().get(0).getName());
        assertEquals("b.txt", tree.getEntries().get(1).getName());
        assertEquals("subdir", tree.getEntries().get(2).getName());

        assertNull(tree.getEntry("nonexistent"));
        assertEquals(entryA.getHash(), tree.getEntry("a.txt").getHash());
        assertEquals(ObjectType.TREE, tree.getType());

        // TreeEntry attributes
        assertFalse(entryA.isDirectory());
        assertTrue(entryDir.isDirectory());
        assertEquals(100644, entryA.getMode());

        // Tree Serialization & Deserialization
        byte[] serialized = tree.serialize();
        assertNotNull(serialized);

        Tree deserialized = Tree.deserialize(serialized);
        assertEquals(3, deserialized.getEntries().size());
        assertEquals("a.txt", deserialized.getEntries().get(0).getName());
    }

    @Test
    public void testConflictNode() {
        ConflictNode node = new ConflictNode("ancestor", "left", "right", "src/conflict.txt");
        assertEquals("ancestor", node.getAncestorHash());
        assertEquals("left", node.getLeftHash());
        assertEquals("right", node.getRightHash());
        assertEquals("src/conflict.txt", node.getPath());
        assertEquals(ObjectType.CONFLICT, node.getType());

        byte[] serialized = node.serialize();
        assertNotNull(serialized);

        ConflictNode deserialized = ConflictNode.deserialize(serialized);
        assertEquals(node.getPath(), deserialized.getPath());
        assertEquals(node.getLeftHash(), deserialized.getLeftHash());
        assertEquals(node.getRightHash(), deserialized.getRightHash());
        assertEquals(node.getAncestorHash(), deserialized.getAncestorHash());
    }

    @Test
    public void testDeltaBlob() {
        byte[] deltaCmds = { 0x01, 0x00, 0x00, 0x00, 0x05 };
        DeltaBlob delta = new DeltaBlob("baseHash123", deltaCmds);
        assertEquals("baseHash123", delta.getBaseBlobHash());
        assertArrayEquals(deltaCmds, delta.getDeltaBytes());
        assertEquals(ObjectType.DELTA_BLOB, delta.getType());

        byte[] serialized = delta.serialize();
        assertNotNull(serialized);

        DeltaBlob deserialized = DeltaBlob.deserialize(serialized);
        assertEquals(delta.getBaseBlobHash(), deserialized.getBaseBlobHash());
        assertArrayEquals(delta.getDeltaBytes(), deserialized.getDeltaBytes());
    }

    @Test
    public void testChunkTree() {
        ChunkTree chunkTree = new ChunkTree(
                Arrays.asList("hash1", "hash2"),
                Arrays.asList(100, 200),
                300L
        );

        assertEquals(2, chunkTree.getChunkHashes().size());
        assertEquals("hash1", chunkTree.getChunkHashes().get(0));
        assertEquals(2, chunkTree.getChunkSizes().size());
        assertEquals(100, chunkTree.getChunkSizes().get(0));
        assertEquals(300L, chunkTree.getTotalSize());
        assertEquals(ObjectType.CHUNK_TREE, chunkTree.getType());

        byte[] serialized = chunkTree.serialize();
        assertNotNull(serialized);

        ChunkTree deserialized = ChunkTree.deserialize(serialized);
        assertEquals(chunkTree.getTotalSize(), deserialized.getTotalSize());
        assertEquals(chunkTree.getChunkHashes().size(), deserialized.getChunkHashes().size());
        assertEquals(chunkTree.getChunkSizes().get(1), deserialized.getChunkSizes().get(1));
    }
}
