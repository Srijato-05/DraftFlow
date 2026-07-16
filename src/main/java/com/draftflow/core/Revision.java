/**
 * @file Revision.java
 * @description Commit revision manifest node representing snapshots in the DAG graph.
 * Connects root directory tree hashes, ancestors list, author metadata details,
 * and cryptographic validation keys.
 * 
 * DESIGN RATIONALE:
 * - Employs Gerrit-style stable Change ID values to track the logical flow of a code change
 *   even as it undergoes edits, rebase actions, or cherry-picks.
 * - Supports background shadow/draft revisions (`draft` flag) to enable automatic workspace backups
 *   without polluting head branch pointers.
 * - Extracts signing data arrays in a deterministic text layout to verify author keys.
 */

package com.draftflow.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Revision implements DraftFlowObject {
    private static final Gson GSON = new GsonBuilder().create();

    private final String treeHash;
    private final List<String> parentHashes;
    private final String changeId; // Stable Change ID across revisions
    private final String author;
    private final long timestamp;
    private final String message;
    private final boolean draft; // Indicates if this is a background shadow commit
    private final String signature; // Base64 signature
    private final String publicKey; // Base64 public key

    public Revision(String treeHash, List<String> parentHashes, String changeId, String author, long timestamp, String message, boolean draft) {
        this(treeHash, parentHashes, changeId, author, timestamp, message, draft, null, null);
    }

    public Revision(String treeHash, List<String> parentHashes, String changeId, String author, long timestamp, String message, boolean draft, String signature, String publicKey) {
        this.treeHash = treeHash;
        this.parentHashes = parentHashes != null ? new ArrayList<>(parentHashes) : new ArrayList<>();
        this.changeId = changeId != null ? changeId : UUID.randomUUID().toString();
        this.author = author != null ? author : System.getProperty("user.name");
        this.timestamp = timestamp > 0 ? timestamp : System.currentTimeMillis();
        this.message = message != null ? message : "";
        this.draft = draft;
        this.signature = signature;
        this.publicKey = publicKey;
    }

    public String getTreeHash() {
        return treeHash;
    }

    public List<String> getParentHashes() {
        return Collections.unmodifiableList(parentHashes);
    }

    public String getChangeId() {
        return changeId;
    }

    public String getAuthor() {
        return author;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getMessage() {
        return message;
    }

    public boolean isDraft() {
        return draft;
    }

    public String getSignature() {
        return signature;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public byte[] getSigningData() {
        StringBuilder sb = new StringBuilder();
        sb.append(treeHash != null ? treeHash : "").append("\n");
        if (parentHashes != null) {
            for (String p : parentHashes) {
                sb.append(p).append(",");
            }
        }
        sb.append("\n");
        sb.append(changeId != null ? changeId : "").append("\n");
        sb.append(author != null ? author : "").append("\n");
        sb.append(timestamp).append("\n");
        sb.append(message != null ? message : "").append("\n");
        sb.append(draft).append("\n");
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override
    public ObjectType getType() {
        return ObjectType.REVISION;
    }

    @Override
    public byte[] serialize() {
        return GSON.toJson(this).getBytes();
    }

    public static Revision deserialize(byte[] data) {
        return GSON.fromJson(new String(data), Revision.class);
    }
}
