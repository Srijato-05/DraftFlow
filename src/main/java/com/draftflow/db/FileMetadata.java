/**
 * @file FileMetadata.java
 * @description In-memory and database representation of working copy file states.
 * Keeps track of metadata details used during workspace scans to compute additions, modifications, or deletions.
 * 
 * DESIGN RATIONALE:
 * - Scanning large files and calculating SHA-256 hashes on every check cycle is CPU-heavy.
 * - This class caches fast filesystem traits (file sizes and system modification timestamps)
 *   so that unchanged files can be skipped instantly during directory diff runs.
 */

package com.draftflow.db;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class FileMetadata {
    private static final Gson GSON = new GsonBuilder().create();

    private final String path;
    private final long size;
    private final long lastModified;
    private final String hash;
    private final String type; // BLOB or CHUNK_TREE
    private final int mode;

    public FileMetadata(String path, long size, long lastModified, String hash, String type, int mode) {
        this.path = path;
        this.size = size;
        this.lastModified = lastModified;
        this.hash = hash;
        this.type = type;
        this.mode = mode;
    }

    public String getPath() {
        return path;
    }

    public long getSize() {
        return size;
    }

    public long getLastModified() {
        return lastModified;
    }

    public String getHash() {
        return hash;
    }

    public String getType() {
        return type;
    }

    public int getMode() {
        return mode;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static FileMetadata fromJson(String json) {
        try {
            return GSON.fromJson(json, FileMetadata.class);
        } catch (Exception e) {
            System.err.println("Warning: Failed to parse FileMetadata JSON: " + json + ". Error: " + e.getMessage());
            return null;
        }
    }
}
