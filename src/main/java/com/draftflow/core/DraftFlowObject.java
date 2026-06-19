package com.draftflow.core;

public interface DraftFlowObject {
    ObjectType getType();
    byte[] serialize();
    
    default String getHash() {
        return Hasher.hash(serializeWithHeader());
    }

    default byte[] serializeWithHeader() {
        byte[] payload = serialize();
        String header = getType().name().toLowerCase() + " " + payload.length + "\0";
        byte[] headerBytes = header.getBytes();
        byte[] combined = new byte[headerBytes.length + payload.length];
        System.arraycopy(headerBytes, 0, combined, 0, headerBytes.length);
        System.arraycopy(payload, 0, combined, headerBytes.length, payload.length);
        return combined;
    }
}
