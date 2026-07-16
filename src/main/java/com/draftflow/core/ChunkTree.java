/**
 * @file ChunkTree.java
 * @description Sequence tree mapping container for large files split by FastCDC.
 * Lists ordered chunk hashes and sizes, representing files exceeding the chunking threshold.
 * 
 * DESIGN RATIONALE:
 * - Direct indexing of large multi-megabyte blobs stalls network transfers and object database scans.
 * - This container tracks the sequence of smaller chunks, enabling deduplication of common byte blocks,
 *   lazy chunk streaming, and partial downloads.
 */

package com.draftflow.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChunkTree implements DraftFlowObject {
    private static final Gson GSON = new GsonBuilder().create();

    private final List<String> chunkHashes;
    private final List<Integer> chunkSizes;
    private final long totalSize;

    public ChunkTree(List<String> chunkHashes, List<Integer> chunkSizes, long totalSize) {
        this.chunkHashes = chunkHashes != null ? new ArrayList<>(chunkHashes) : new ArrayList<>();
        this.chunkSizes = chunkSizes != null ? new ArrayList<>(chunkSizes) : new ArrayList<>();
        this.totalSize = totalSize;
    }

    public List<String> getChunkHashes() {
        return Collections.unmodifiableList(chunkHashes);
    }

    public List<Integer> getChunkSizes() {
        return Collections.unmodifiableList(chunkSizes);
    }

    public long getTotalSize() {
        return totalSize;
    }

    @Override
    public ObjectType getType() {
        return ObjectType.CHUNK_TREE;
    }

    @Override
    public byte[] serialize() {
        return GSON.toJson(this).getBytes();
    }

    public static ChunkTree deserialize(byte[] data) {
        return GSON.fromJson(new String(data), ChunkTree.class);
    }
}
