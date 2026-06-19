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

    public Revision(String treeHash, List<String> parentHashes, String changeId, String author, long timestamp, String message, boolean draft) {
        this.treeHash = treeHash;
        this.parentHashes = parentHashes != null ? new ArrayList<>(parentHashes) : new ArrayList<>();
        this.changeId = changeId != null ? changeId : UUID.randomUUID().toString();
        this.author = author != null ? author : System.getProperty("user.name");
        this.timestamp = timestamp > 0 ? timestamp : System.currentTimeMillis();
        this.message = message != null ? message : "";
        this.draft = draft;
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
