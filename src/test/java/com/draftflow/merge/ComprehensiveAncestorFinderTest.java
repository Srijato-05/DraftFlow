package com.draftflow.merge;

import com.draftflow.core.CAS;
import com.draftflow.core.Revision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class ComprehensiveAncestorFinderTest {

    @TempDir
    Path tempDir;

    private CAS cas;

    @BeforeEach
    public void setUp() throws IOException {
        cas = new CAS(tempDir);
        cas.init();
    }

    private String createRevision(String msg, String... parents) throws IOException {
        Revision rev = new Revision(
                "treehash",
                Arrays.asList(parents),
                "change-id",
                "author",
                System.currentTimeMillis(),
                msg,
                false
        );
        return cas.writeObject(rev);
    }

    @Test
    public void testIdentityAndNull() throws IOException {
        String rev = createRevision("commit 1");
        
        // LCA with itself
        assertEquals(rev, AncestorFinder.findLCA(rev, rev, cas));

        // LCA with null inputs
        assertNull(AncestorFinder.findLCA(null, rev, cas));
        assertNull(AncestorFinder.findLCA(rev, null, cas));
    }

    @Test
    public void testDirectAncestor() throws IOException {
        // C1 -> C2 -> C3
        String c1 = createRevision("commit 1");
        String c2 = createRevision("commit 2", c1);
        String c3 = createRevision("commit 3", c2);

        // LCA of C3 and C1 is C1
        assertEquals(c1, AncestorFinder.findLCA(c3, c1, cas));
        // LCA of C3 and C2 is C2
        assertEquals(c2, AncestorFinder.findLCA(c3, c2, cas));
    }

    @Test
    public void testForkTopology() throws IOException {
        //       C1
        //      /  \
        //    C2    C3
        //    |      |
        //    C4    C5
        String c1 = createRevision("commit 1");
        String c2 = createRevision("commit 2", c1);
        String c3 = createRevision("commit 3", c1);
        String c4 = createRevision("commit 4", c2);
        String c5 = createRevision("commit 5", c3);

        // LCA of C4 and C5 is C1
        assertEquals(c1, AncestorFinder.findLCA(c4, c5, cas));
        // LCA of C4 and C3 is C1
        assertEquals(c1, AncestorFinder.findLCA(c4, c3, cas));
        // LCA of C2 and C5 is C1
        assertEquals(c1, AncestorFinder.findLCA(c2, c5, cas));
    }

    @Test
    public void testDisconnectedTopology() throws IOException {
        // C1 -> C2
        // D1 -> D2
        String c1 = createRevision("commit C1");
        String c2 = createRevision("commit C2", c1);

        String d1 = createRevision("commit D1");
        String d2 = createRevision("commit D2", d1);

        // LCA should be null as they do not share any ancestor
        assertNull(AncestorFinder.findLCA(c2, d2, cas));
    }

    @Test
    public void testCrissCrossMergeTopology() throws IOException {
        //       Base
        //      /    \
        //     A      B
        //     |\    /|
        //     | \  / |
        //     |  \/  |
        //     |  /\  |
        //     | /  \ |
        //     |/    \|
        //     X      Y
        // (X merges A and B; Y merges B and A)
        String base = createRevision("base");
        String a = createRevision("commit A", base);
        String b = createRevision("commit B", base);

        String x = createRevision("merge X", a, b);
        String y = createRevision("merge Y", b, a);

        // Finding LCA of X and Y should return either A or B
        String lca = AncestorFinder.findLCA(x, y, cas);
        assertTrue(lca.equals(a) || lca.equals(b));
    }
}
