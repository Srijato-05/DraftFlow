/**
 * @file TreeDiffer.java
 * @description Hierarchical snapshot diffing engine for DraftFlow VCS.
 * Performs recursive tree comparisons between revision tree structures, computing file modifications,
 * additions, deletions, or type switches.
 * 
 * DESIGN RATIONALE:
 * - Uses early-exit subtree comparisons: if subfolder hashes are identical, their child nodes are skipped,
 *   substantially reducing disk reads on deep repositories.
 * - Handles type mutations (e.g. replacing a file with a folder or vice-versa) by emitting paired
 *   DELETED and ADDED diff events.
 * - Outputs alphabetical list collections to ensure deterministic visual display in the frontend.
 */

package com.draftflow.diff;

import com.draftflow.core.*;
import java.io.IOException;
import java.util.*;

public class TreeDiffer {

    /**
     * Compares two trees identified by their tree hashes in the CAS.
     * Returns a list of FileDiffs representing the changes from leftTree to rightTree.
     */
    public static List<FileDiff> diff(String leftTreeHash, String rightTreeHash, CAS cas) throws IOException {
        List<FileDiff> diffs = new ArrayList<>();
        diffRecursive("", leftTreeHash, rightTreeHash, cas, diffs);
        // Sort diffs by path alphabetically
        diffs.sort(Comparator.comparing(FileDiff::getPath));
        return diffs;
    }

    private static void diffRecursive(String currentPath, String leftHash, String rightHash, CAS cas, List<FileDiff> diffs) throws IOException {
        if (Objects.equals(leftHash, rightHash)) {
            return; // Subtree/file identical, no changes
        }

        Tree leftTree = (leftHash != null) ? (Tree) cas.readObject(leftHash) : new Tree(null);
        Tree rightTree = (rightHash != null) ? (Tree) cas.readObject(rightHash) : new Tree(null);

        Map<String, TreeEntry> leftEntries = new HashMap<>();
        for (TreeEntry e : leftTree.getEntries()) {
            leftEntries.put(e.getName(), e);
        }

        Map<String, TreeEntry> rightEntries = new HashMap<>();
        for (TreeEntry e : rightTree.getEntries()) {
            rightEntries.put(e.getName(), e);
        }

        Set<String> allNames = new HashSet<>();
        allNames.addAll(leftEntries.keySet());
        allNames.addAll(rightEntries.keySet());

        for (String name : allNames) {
            TreeEntry left = leftEntries.get(name);
            TreeEntry right = rightEntries.get(name);
            String relPath = currentPath.isEmpty() ? name : currentPath + "/" + name;

            if (left != null && right == null) {
                // Deleted
                if (left.getType() == ObjectType.TREE) {
                    markAllDeleted(relPath, left.getHash(), cas, diffs);
                } else {
                    diffs.add(new FileDiff(relPath, DiffType.DELETED, left.getHash(), null, left.getMode(), 0));
                }
            } else if (left == null && right != null) {
                // Added
                if (right.getType() == ObjectType.TREE) {
                    markAllAdded(relPath, right.getHash(), cas, diffs);
                } else {
                    diffs.add(new FileDiff(relPath, DiffType.ADDED, null, right.getHash(), 0, right.getMode()));
                }
            } else {
                // Both exist but hashes are different (we know leftHash != rightHash)
                if (left.getType() == ObjectType.TREE && right.getType() == ObjectType.TREE) {
                    // Both are directories, recurse
                    diffRecursive(relPath, left.getHash(), right.getHash(), cas, diffs);
                } else if (left.getType() != ObjectType.TREE && right.getType() != ObjectType.TREE) {
                    // Both are files, check if content or mode changed
                    if (!left.getHash().equals(right.getHash()) || left.getMode() != right.getMode()) {
                        diffs.add(new FileDiff(relPath, DiffType.MODIFIED, left.getHash(), right.getHash(), left.getMode(), right.getMode()));
                    }
                } else {
                    // One is directory, other is file (Type change)
                    // Treat as deletion of old type followed by addition of new type
                    if (left.getType() == ObjectType.TREE) {
                        markAllDeleted(relPath, left.getHash(), cas, diffs);
                    } else {
                        diffs.add(new FileDiff(relPath, DiffType.DELETED, left.getHash(), null, left.getMode(), 0));
                    }

                    if (right.getType() == ObjectType.TREE) {
                        markAllAdded(relPath, right.getHash(), cas, diffs);
                    } else {
                        diffs.add(new FileDiff(relPath, DiffType.ADDED, null, right.getHash(), 0, right.getMode()));
                    }
                }
            }
        }
    }

    private static void markAllAdded(String currentPath, String hash, CAS cas, List<FileDiff> diffs) throws IOException {
        Tree tree = (Tree) cas.readObject(hash);
        for (TreeEntry entry : tree.getEntries()) {
            String relPath = currentPath + "/" + entry.getName();
            if (entry.getType() == ObjectType.TREE) {
                markAllAdded(relPath, entry.getHash(), cas, diffs);
            } else {
                diffs.add(new FileDiff(relPath, DiffType.ADDED, null, entry.getHash(), 0, entry.getMode()));
            }
        }
    }

    private static void markAllDeleted(String currentPath, String hash, CAS cas, List<FileDiff> diffs) throws IOException {
        Tree tree = (Tree) cas.readObject(hash);
        for (TreeEntry entry : tree.getEntries()) {
            String relPath = currentPath + "/" + entry.getName();
            if (entry.getType() == ObjectType.TREE) {
                markAllDeleted(relPath, entry.getHash(), cas, diffs);
            } else {
                diffs.add(new FileDiff(relPath, DiffType.DELETED, entry.getHash(), null, entry.getMode(), 0));
            }
        }
    }
}
