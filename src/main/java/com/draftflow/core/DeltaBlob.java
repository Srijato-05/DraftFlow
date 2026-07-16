/**
 * @file DeltaBlob.java
 * @description Storage container for delta-compressed blobs.
 * Packages base blob hash references and delta command byte arrays.
 * 
 * DESIGN RATIONALE:
 * - Storing full duplicate copies of text revisions under minor edits wastes storage space.
 * - This container tracks only the differences relative to a base hash.
 *   When retrieved, the engine recursively fetches the base object and applies the binary
 *   delta commands to reconstruct the requested content.
 */

package com.draftflow.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class DeltaBlob implements DraftFlowObject {
    private static final Gson GSON = new GsonBuilder().create();

    private final String baseBlobHash;
    private final String deltaBytesBase64;

    public DeltaBlob(String baseBlobHash, byte[] deltaBytes) {
        this.baseBlobHash = baseBlobHash;
        this.deltaBytesBase64 = java.util.Base64.getEncoder().encodeToString(deltaBytes);
    }

    public String getBaseBlobHash() {
        return baseBlobHash;
    }

    public byte[] getDeltaBytes() {
        return java.util.Base64.getDecoder().decode(deltaBytesBase64);
    }

    @Override
    public ObjectType getType() {
        return ObjectType.DELTA_BLOB;
    }

    @Override
    public byte[] serialize() {
        return GSON.toJson(this).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public static DeltaBlob deserialize(byte[] data) {
        return GSON.fromJson(new String(data, java.nio.charset.StandardCharsets.UTF_8), DeltaBlob.class);
    }
}
