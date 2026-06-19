package com.draftflow.core;

public class TreeEntry implements Comparable<TreeEntry> {
    private final String name;
    private final String hash;
    private final ObjectType type;
    private final int mode; // 100644 = regular, 100755 = exec, 040000 = tree, etc.

    public TreeEntry(String name, String hash, ObjectType type, int mode) {
        this.name = name;
        this.hash = hash;
        this.type = type;
        this.mode = mode;
    }

    public String getName() {
        return name;
    }

    public String getHash() {
        return hash;
    }

    public ObjectType getType() {
        return type;
    }

    public int getMode() {
        return mode;
    }

    public boolean isDirectory() {
        return type == ObjectType.TREE;
    }

    @Override
    public int compareTo(TreeEntry o) {
        return this.name.compareTo(o.name);
    }
}
