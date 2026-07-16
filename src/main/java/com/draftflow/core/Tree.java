/**
 * @file Tree.java
 * @description Hierarchical directory snapshot representation in DraftFlow VCS.
 * Maps folder listings, linking filenames to their target child blobs or nested subdirectories.
 * 
 * DESIGN RATIONALE:
 * - Employs a sorted collection of tree entry nodes to represent file system layouts.
 * - Enforces alphabetical order sorting on entries during initialization. This guarantees deterministic
 *   hashing of directory snapshots so that filesystem traversal sequence does not alter the hash of the tree.
 */

package com.draftflow.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Tree implements DraftFlowObject {
    private static final Gson GSON = new GsonBuilder().create();
    private final List<TreeEntry> entries;

    public Tree(List<TreeEntry> entries) {
        this.entries = entries != null ? new ArrayList<>(entries) : new ArrayList<>();
        Collections.sort(this.entries);
    }

    public List<TreeEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public TreeEntry getEntry(String name) {
        for (TreeEntry entry : entries) {
            if (entry.getName().equals(name)) {
                return entry;
            }
        }
        return null;
    }

    @Override
    public ObjectType getType() {
        return ObjectType.TREE;
    }

    @Override
    public byte[] serialize() {
        return GSON.toJson(this).getBytes();
    }

    public static Tree deserialize(byte[] data) {
        return GSON.fromJson(new String(data), Tree.class);
    }
}
