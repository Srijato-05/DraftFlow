/**
 * @file ConflictNode.java
 * @description Persistent 3-way merge conflict metadata container.
 * Stores references to the ancestor, left (ours), and right (theirs) file states during conflict states.
 * 
 * DESIGN RATIONALE:
 * - When a tree merge contains conflicts, the workspace cannot receive a clean file snapshot immediately.
 * - This container persists the exact hashes of the conflicting states in the object database.
 *   The REST API can then read this container to render side-by-side comparisons on the dashboard.
 */

package com.draftflow.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class ConflictNode implements DraftFlowObject {
    private static final Gson GSON = new GsonBuilder().create();

    private final String ancestorHash;
    private final String leftHash;
    private final String rightHash;
    private final String path;

    public ConflictNode(String ancestorHash, String leftHash, String rightHash, String path) {
        this.ancestorHash = ancestorHash;
        this.leftHash = leftHash;
        this.rightHash = rightHash;
        this.path = path;
    }

    public String getAncestorHash() {
        return ancestorHash;
    }

    public String getLeftHash() {
        return leftHash;
    }

    public String getRightHash() {
        return rightHash;
    }

    public String getPath() {
        return path;
    }

    @Override
    public ObjectType getType() {
        return ObjectType.CONFLICT;
    }

    @Override
    public byte[] serialize() {
        return GSON.toJson(this).getBytes();
    }

    public static ConflictNode deserialize(byte[] data) {
        return GSON.fromJson(new String(data), ConflictNode.class);
    }
}
