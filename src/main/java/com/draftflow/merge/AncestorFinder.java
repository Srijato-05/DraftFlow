/**
 * @file AncestorFinder.java
 * @description Lowest Common Ancestor (LCA) graph search utility for DraftFlow DAGs.
 * Traverses commit revision lineage paths to locate the common ancestor node of two target branches.
 * 
 * DESIGN RATIONALE:
 * - A 3-way merge is impossible without knowing the baseline "common parent" revision.
 * - This class implements a double-pass Breadth-First Search (BFS):
 *   1. Traverses and catalogs all ancestors of revision A.
 *   2. Traverses revision B, returning the first intersection node present in A's ancestor catalog.
 */

package com.draftflow.merge;

import com.draftflow.core.CAS;
import com.draftflow.core.Revision;

import java.io.IOException;
import java.util.*;

public class AncestorFinder {

    /**
     * Finds the Lowest Common Ancestor (LCA) between two revisions in the DAG.
     * Returns the revision hash of the common ancestor, or null if no common ancestor exists.
     */
    public static String findLCA(String revA, String revB, CAS cas) throws IOException {
        if (Objects.equals(revA, revB)) {
            return revA;
        }

        // BFS to collect all ancestors of revA
        Set<String> ancestorsA = new HashSet<>();
        Queue<String> queueA = new LinkedList<>();
        queueA.add(revA);

        while (!queueA.isEmpty()) {
            String curr = queueA.poll();
            if (curr == null) continue;
            if (ancestorsA.add(curr)) {
                Revision r = (Revision) cas.readObject(curr);
                queueA.addAll(r.getParentHashes());
            }
        }

        // BFS from revB to find the first ancestor that exists in ancestorsA
        Queue<String> queueB = new LinkedList<>();
        Set<String> visitedB = new HashSet<>();
        queueB.add(revB);

        while (!queueB.isEmpty()) {
            String curr = queueB.poll();
            if (curr == null) continue;
            if (visitedB.add(curr)) {
                if (ancestorsA.contains(curr)) {
                    return curr; // Found lowest common ancestor
                }
                Revision r = (Revision) cas.readObject(curr);
                queueB.addAll(r.getParentHashes());
            }
        }

        return null; // Disconnected revision components
    }
}
