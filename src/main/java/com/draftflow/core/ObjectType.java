/**
 * @file ObjectType.java
 * @description Type definitions for items stored inside the Content Addressable Storage (CAS) database.
 * 
 * DESIGN RATIONALE:
 * - Employs explicit type separation to cleanly handle storage structures:
 *   - BLOB: Monolithic file content data.
 *   - TREE: Folder structures mapping names and permissions to child object hashes.
 *   - REVISION: History node carrying author info, signature, and tree pointer.
 *   - CONFLICT: Stored marker tracking 3-way merge conflict states.
 *   - CHUNK_TREE: Pointer mapping sequence listings of FastCDC split blobs.
 *   - DELTA_BLOB: Forward/backward binary diff byte block.
 */

package com.draftflow.core;

public enum ObjectType {
    BLOB,
    TREE,
    REVISION,
    CONFLICT,
    CHUNK_TREE,
    DELTA_BLOB
}
