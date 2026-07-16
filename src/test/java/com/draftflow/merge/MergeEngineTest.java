package com.draftflow.merge;

import com.draftflow.core.*;
import com.draftflow.db.MetadataStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class MergeEngineTest {

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

        wm = new WorkspaceManager(cas, db);
    }

    @AfterEach
    public void tearDown() {
        if (db != null) {
            db.close();
        }
    }

    @Test
    public void testLcaResolution() throws IOException {
        // Create initial base commit
        String baseTree = cas.writeObject(new Tree(new ArrayList<>()));
        Revision baseRev = new Revision(baseTree, new ArrayList<>(), "change-1", "author", 0, "base", false);
        String baseHash = cas.writeObject(baseRev);

        // Branch A
        Revision revA = new Revision(baseTree, Arrays.asList(baseHash), "change-1", "author", 0, "rev A", false);
        String hashA = cas.writeObject(revA);

        // Branch B
        Revision revB = new Revision(baseTree, Arrays.asList(baseHash), "change-1", "author", 0, "rev B", false);
        String hashB = cas.writeObject(revB);

        // LCA of A and B should be base
        String lca = AncestorFinder.findLCA(hashA, hashB, cas);
        assertEquals(baseHash, lca);
    }

    @Test
    public void testCleanThreeWayMerge() throws IOException {
        // --- 1. Base State ---
        Path file = workDir.resolve("code.txt");
        Files.writeString(file, "line 1\nline 2\nline 3");
        
        db.setConfig("activeHead", "heads/main");
        String baseRev = wm.scanAndCreateShadowCommit(Collections.singleton(file));

        // --- 2. Ours State (modify line 1) ---
        Files.writeString(file, "line 1 modified\nline 2\nline 3");
        String oursRev = wm.scanAndCreateShadowCommit(Collections.singleton(file));

        // --- 3. Theirs State (modify line 3 from base state) ---
        // Checkout base first
        wm.restoreWorkingCopy(baseRev);
        Files.writeString(file, "line 1\nline 2\nline 3 modified");
        String theirsRev = wm.scanAndCreateShadowCommit(Collections.singleton(file));

        // --- 4. Perform Merge ---
        MergeEngine.MergeResult result = MergeEngine.mergeRevisions(oursRev, theirsRev, cas);
        assertTrue(result.clean);
        assertTrue(result.conflicts.isEmpty());

        // Restore merged tree to working dir to verify contents
        wm.restoreWorkingCopy(oursRev); // checkout ours first to satisfy active branch
        
        // Materialize the merged tree manually using a dummy revision
        Revision mergedRev = new Revision(result.treeHash, Arrays.asList(oursRev, theirsRev), "merged-change", "author", 0, "merge", false);
        String mergedRevHash = cas.writeObject(mergedRev);
        
        wm.restoreWorkingCopy(mergedRevHash);

        String mergedContent = Files.readString(file).replace("\r\n", "\n");
        assertEquals("line 1 modified\nline 2\nline 3 modified", mergedContent);
    }

    @Test
    public void testConflictingThreeWayMerge() throws IOException {
        // --- 1. Base State ---
        Path file = workDir.resolve("code.txt");
        Files.writeString(file, "line 1\nline 2\nline 3");
        
        db.setConfig("activeHead", "heads/main");
        String baseRev = wm.scanAndCreateShadowCommit(Collections.singleton(file));

        // --- 2. Ours State (modify line 2 to A) ---
        Files.writeString(file, "line 1\nline 2 modified A\nline 3");
        String oursRev = wm.scanAndCreateShadowCommit(Collections.singleton(file));

        // --- 3. Theirs State (modify line 2 to B) ---
        wm.restoreWorkingCopy(baseRev);
        Files.writeString(file, "line 1\nline 2 modified B\nline 3");
        String theirsRev = wm.scanAndCreateShadowCommit(Collections.singleton(file));

        // --- 4. Perform Merge ---
        MergeEngine.MergeResult result = MergeEngine.mergeRevisions(oursRev, theirsRev, cas);
        assertFalse(result.clean);
        assertEquals(1, result.conflicts.size());
        assertEquals("code.txt", result.conflicts.get(0));

        // Restore to working copy to see conflict markers
        Revision mergedRev = new Revision(result.treeHash, Arrays.asList(oursRev, theirsRev), "merged-change", "author", 0, "merge", false);
        String mergedRevHash = cas.writeObject(mergedRev);
        
        wm.restoreWorkingCopy(mergedRevHash);

        String mergedContent = Files.readString(file).replace("\r\n", "\n");
        assertTrue(mergedContent.contains("<<<<<<< OURS"));
        assertTrue(mergedContent.contains("line 2 modified A"));
        assertTrue(mergedContent.contains("======="));
        assertTrue(mergedContent.contains("line 2 modified B"));
        assertTrue(mergedContent.contains(">>>>>>> THEIRS"));
    }
}
