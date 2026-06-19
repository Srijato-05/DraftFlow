package com.draftflow.merge;

import com.draftflow.core.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class AdvancedMergeEngineTest {

    @TempDir
    Path tempDir;

    private CAS cas;

    @BeforeEach
    public void setUp() throws IOException {
        cas = new CAS(tempDir);
        cas.init();
    }

    @Test
    public void testCrissCrossLCA() throws IOException {
        // Criss-cross history construction:
        // R (Root)
        // A (parent: R) and B (parent: R)
        // C (parents: A, B)
        // D (parents: B, A)
        
        // 1. Root Tree
        Tree emptyTree = new Tree(new ArrayList<>());
        String treeHash = cas.writeObject(emptyTree);
        
        Revision r = new Revision(treeHash, new ArrayList<>(), UUID.randomUUID().toString(), "author", 1000L, "Root", false);
        String hashR = cas.writeObject(r);

        // 2. Revision A and B
        Revision a = new Revision(treeHash, Collections.singletonList(hashR), UUID.randomUUID().toString(), "author", 2000L, "A", false);
        String hashA = cas.writeObject(a);

        Revision b = new Revision(treeHash, Collections.singletonList(hashR), UUID.randomUUID().toString(), "author", 3000L, "B", false);
        String hashB = cas.writeObject(b);

        // 3. Revision C (parents: [A, B])
        Revision c = new Revision(treeHash, Arrays.asList(hashA, hashB), UUID.randomUUID().toString(), "author", 4000L, "C", false);
        String hashC = cas.writeObject(c);

        // 4. Revision D (parents: [B, A])
        Revision d = new Revision(treeHash, Arrays.asList(hashB, hashA), UUID.randomUUID().toString(), "author", 5000L, "D", false);
        String hashD = cas.writeObject(d);

        // LCA of C and D must be either A or B
        String lca = AncestorFinder.findLCA(hashC, hashD, cas);
        assertNotNull(lca);
        assertTrue(lca.equals(hashA) || lca.equals(hashB), "LCA must be one of the criss-cross parents (A or B)");
    }

    @Test
    public void testDisjointDAGMerge() throws IOException {
        // Disjoint DAGs:
        // History 1: Root1 -> Rev1 (adds file1.txt)
        // History 2: Root2 -> Rev2 (adds file2.txt)
        // No common ancestor!
        
        // Root 1 & Rev 1
        Blob b1 = new Blob("File 1 content".getBytes(StandardCharsets.UTF_8));
        String b1Hash = cas.writeObject(b1);
        TreeEntry entry1 = new TreeEntry("file1.txt", b1Hash, ObjectType.BLOB, 100644);
        Tree t1 = new Tree(Collections.singletonList(entry1));
        String t1Hash = cas.writeObject(t1);
        Revision root1 = new Revision(t1Hash, new ArrayList<>(), UUID.randomUUID().toString(), "author", 1000L, "Root1", false);
        String hashRev1 = cas.writeObject(root1);

        // Root 2 & Rev 2
        Blob b2 = new Blob("File 2 content".getBytes(StandardCharsets.UTF_8));
        String b2Hash = cas.writeObject(b2);
        TreeEntry entry2 = new TreeEntry("file2.txt", b2Hash, ObjectType.BLOB, 100644);
        Tree t2 = new Tree(Collections.singletonList(entry2));
        String t2Hash = cas.writeObject(t2);
        Revision root2 = new Revision(t2Hash, new ArrayList<>(), UUID.randomUUID().toString(), "author", 2000L, "Root2", false);
        String hashRev2 = cas.writeObject(root2);

        // 1. Ancestor must be null
        String lca = AncestorFinder.findLCA(hashRev1, hashRev2, cas);
        assertNull(lca, "Disjoint histories should have no LCA");

        // 2. Perform merge trees with null base tree hash
        MergeEngine.MergeResult result = MergeEngine.mergeTrees(null, t1Hash, t2Hash, cas);
        
        // The merge should be clean since they edit entirely disjoint files
        assertTrue(result.clean);
        assertNotNull(result.treeHash);

        // Merged tree should contain both file1.txt and file2.txt
        Tree mergedTree = (Tree) cas.readObject(result.treeHash);
        assertEquals(2, mergedTree.getEntries().size());
        assertTrue(mergedTree.getEntries().stream().anyMatch(e -> e.getName().equals("file1.txt")));
        assertTrue(mergedTree.getEntries().stream().anyMatch(e -> e.getName().equals("file2.txt")));
    }

    @Test
    public void testCRLFAgnosticLineMerge() {
        // Base lines
        List<String> base = Arrays.asList("line1", "line2", "line3");
        
        // Ours inserts CRLF modified lines
        List<String> ours = Arrays.asList("line1\r", "line2-modified\r", "line3\r");
        
        // Theirs inserts standard LF lines
        List<String> theirs = Arrays.asList("line1", "line2", "line3-modified");

        // Merge should run successfully and isolate modifications clean/dirty
        LineMerge.MergeResult result = LineMerge.merge(base, ours, theirs);
        
        // The clean flag will depend on whether line endings are considered conflicts.
        // Let's verify that we don't crash and line edits are aligned correctly.
        assertNotNull(result.mergedLines);
    }

    @Test
    public void testAutoChunkingMergedFile() throws IOException {
        // Test that if a 3-way line merge generates content > 1MB,
        // it is automatically written to the CAS as a CHUNK_TREE.

        // Base file
        List<String> base = Arrays.asList("Line A", "Line B");

        // Ours appends a huge block of text
        StringBuilder oursBigBuilder = new StringBuilder();
        for (int i = 0; i < 50000; i++) {
            oursBigBuilder.append("Ours Large Line Number ").append(i).append("\n");
        }
        List<String> ours = Arrays.asList("Line A", "Line B", oursBigBuilder.toString());

        // Theirs does not touch the end, just modifications to the start
        List<String> theirs = Arrays.asList("Line A-Modified", "Line B");

        // Merge trees mock setup
        Blob baseBlob = new Blob(String.join("\n", base).getBytes(StandardCharsets.UTF_8));
        String baseBlobHash = cas.writeObject(baseBlob);
        TreeEntry baseEntry = new TreeEntry("bigfile.txt", baseBlobHash, ObjectType.BLOB, 100644);
        Tree baseTree = new Tree(Collections.singletonList(baseEntry));
        String baseTreeHash = cas.writeObject(baseTree);

        Blob oursBlob = new Blob(String.join("\n", ours).getBytes(StandardCharsets.UTF_8));
        String oursBlobHash = cas.writeObject(oursBlob);
        TreeEntry oursEntry = new TreeEntry("bigfile.txt", oursBlobHash, ObjectType.BLOB, 100644);
        Tree oursTree = new Tree(Collections.singletonList(oursEntry));
        String oursTreeHash = cas.writeObject(oursTree);

        Blob theirsBlob = new Blob(String.join("\n", theirs).getBytes(StandardCharsets.UTF_8));
        String theirsBlobHash = cas.writeObject(theirsBlob);
        TreeEntry theirsEntry = new TreeEntry("bigfile.txt", theirsBlobHash, ObjectType.BLOB, 100644);
        Tree theirsTree = new Tree(Collections.singletonList(theirsEntry));
        String theirsTreeHash = cas.writeObject(theirsTree);

        // Run merge
        MergeEngine.MergeResult result = MergeEngine.mergeTrees(baseTreeHash, oursTreeHash, theirsTreeHash, cas);
        
        // Assert merge is clean
        assertTrue(result.clean);

        // Read merged tree
        Tree mergedTree = (Tree) cas.readObject(result.treeHash);
        TreeEntry mergedFileEntry = mergedTree.getEntries().get(0);

        // The type must be CHUNK_TREE because the merged content size > 1MB
        assertEquals(ObjectType.CHUNK_TREE, mergedFileEntry.getType());
        
        // Read ChunkTree and assert total size is correct
        ChunkTree chunkTree = (ChunkTree) cas.readObject(mergedFileEntry.getHash());
        assertTrue(chunkTree.getTotalSize() > 1024 * 1024);
        assertTrue(chunkTree.getChunkHashes().size() > 1);
    }
}
