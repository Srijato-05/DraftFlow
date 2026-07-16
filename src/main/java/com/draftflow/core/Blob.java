/**
 * @file Blob.java
 * @description Blob object wrapper for DraftFlow VCS.
 * Represents file version contents inside the Content Addressable Storage (CAS) engine.
 * 
 * DESIGN RATIONALE:
 * - Keeps file storage content-agnostic, treating all files (text, compiled source, small assets)
 *   as raw byte arrays to maintain database integrity and universal compatibility.
 */

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
