/**
 * @file DraftFlowObject.java
 * @description The common interface for all database objects tracked by DraftFlow VCS.
 * Defines serialization protocols, object types, and content-hashing structures.
 * 
 * DESIGN RATIONALE:
 * - Employs a Git-like header format for serialized byte arrays: `[type] [length]\0[payload]`.
 *   This ensures that object type information is bundled together with the content hash,
 *   preventing collision attacks where two different types of objects produce identical raw hashes.
 */

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
