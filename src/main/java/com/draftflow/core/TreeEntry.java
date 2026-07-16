/**
 * @file TreeEntry.java
 * @description Single catalog record within a VCS Tree snapshot directory.
 * Associates a name with its target object database identifier (hash), type classification,
 * and standard Unix file permissions.
 * 
 * DESIGN RATIONALE:
 * - Implements `Comparable<TreeEntry>` based on alphabetical names to enable sorting in
 *   directory structures.
 * - Stores POSIX-compliant integer mode constants (e.g. `0100644` for files, `0100755` for scripts,
 *   and `040000` for subfolders) to support cross-platform execution permissions restoration.
 */

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
