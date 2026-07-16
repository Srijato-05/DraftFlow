/**
 * @file FileDiff.java
 * @description Difference description for a single file between two tree snapshots.
 * Carries path metadata, change categorization type, old/new content hashes, and old/new permissions.
 * 
 * DESIGN RATIONALE:
 * - Simplifies visual updates in the dashboard views by compiling granular differences into
 *   a standard structure. This enables clean layout mapping in the frontend React views.
 */

package com.draftflow.diff;

public class FileDiff {
    private final String path;
    private final DiffType type;
    private final String oldHash;
    private final String newHash;
    private final int oldMode;
    private final int newMode;

    public FileDiff(String path, DiffType type, String oldHash, String newHash, int oldMode, int newMode) {
        this.path = path;
        this.type = type;
        this.oldHash = oldHash;
        this.newHash = newHash;
        this.oldMode = oldMode;
        this.newMode = newMode;
    }

    public String getPath() {
        return path;
    }

    public DiffType getType() {
        return type;
    }

    public String getOldHash() {
        return oldHash;
    }

    public String getNewHash() {
        return newHash;
    }

    public int getOldMode() {
        return oldMode;
    }

    public int getNewMode() {
        return newMode;
    }

    @Override
    public String toString() {
        return "FileDiff{" +
                "path='" + path + '\'' +
                ", type=" + type +
                ", oldHash='" + oldHash + '\'' +
                ", newHash='" + newHash + '\'' +
                ", oldMode=" + oldMode +
                ", newMode=" + newMode +
                '}';
    }
}
