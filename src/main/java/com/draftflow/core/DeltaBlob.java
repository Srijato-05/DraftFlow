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
