package com.draftflow.core;

public class Blob implements DraftFlowObject {
    private final byte[] content;

    public Blob(byte[] content) {
        this.content = content != null ? content : new byte[0];
    }

    public byte[] getContent() {
        return content;
    }

    @Override
    public ObjectType getType() {
        return ObjectType.BLOB;
    }

    @Override
    public byte[] serialize() {
        return content;
    }
}
