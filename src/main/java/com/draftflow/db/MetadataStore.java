package com.draftflow.db;

import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MetadataStore implements AutoCloseable {
    private final Path dbFilePath;
    private MVStore store;
    private MVMap<String, String> indexMap;
    private MVMap<String, String> refMap;
    private MVMap<String, String> changeMap;
    private MVMap<String, String> changeHistoryMap;
    private MVMap<String, String> configMap;

    public MetadataStore(Path dbFilePath) {
        this.dbFilePath = dbFilePath;
    }

    public void open() throws IOException {
        Files.createDirectories(dbFilePath.getParent());
        this.store = new MVStore.Builder()
                .fileName(dbFilePath.toString())
                .open();
        
        this.indexMap = this.store.openMap("index");
        this.refMap = this.store.openMap("refs");
        this.changeMap = this.store.openMap("changes");
        this.changeHistoryMap = this.store.openMap("changeHistory");
        this.configMap = this.store.openMap("config");
    }

    public synchronized void commit() {
        if (store != null && !store.isClosed()) {
            store.commit();
        }
    }

    @Override
    public synchronized void close() {
        if (store != null && !store.isClosed()) {
            store.close();
        }
    }

    // --- Index Cache Operations ---

    public synchronized void putFile(FileMetadata meta) {
        indexMap.put(meta.getPath(), meta.toJson());
    }

    public synchronized FileMetadata getFile(String path) {
        String json = indexMap.get(path);
        return json != null ? FileMetadata.fromJson(json) : null;
    }

    public synchronized void removeFile(String path) {
        indexMap.remove(path);
    }

    public synchronized List<FileMetadata> getAllFiles() {
        List<FileMetadata> list = new ArrayList<>();
        for (String json : indexMap.values()) {
            list.add(FileMetadata.fromJson(json));
        }
        return list;
    }

    public synchronized void clearIndex() {
        indexMap.clear();
    }

    // --- Ref / Branch Operations ---

    public synchronized void setRef(String name, String revisionHash) {
        refMap.put(name, revisionHash);
    }

    public synchronized String getRef(String name) {
        return refMap.get(name);
    }

    public synchronized void removeRef(String name) {
        refMap.remove(name);
    }

    public synchronized List<String> getRefNames() {
        return new ArrayList<>(refMap.keySet());
    }

    // --- Change ID Operations ---

    public synchronized void setChangeRevision(String changeId, String revisionHash) {
        changeMap.put(changeId, revisionHash);
        addRevisionToChangeHistory(changeId, revisionHash);
    }

    public synchronized String getChangeRevision(String changeId) {
        return changeMap.get(changeId);
    }

    private synchronized void addRevisionToChangeHistory(String changeId, String revisionHash) {
        String history = changeHistoryMap.get(changeId);
        if (history == null || history.isEmpty()) {
            changeHistoryMap.put(changeId, revisionHash);
        } else {
            String[] parts = history.split(",");
            if (!parts[parts.length - 1].equals(revisionHash)) {
                changeHistoryMap.put(changeId, history + "," + revisionHash);
            }
        }
    }

    public synchronized List<String> getChangeHistory(String changeId) {
        String history = changeHistoryMap.get(changeId);
        if (history == null || history.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(List.of(history.split(",")));
    }

    // --- Config Operations ---

    public synchronized void setConfig(String key, String value) {
        configMap.put(key, value);
    }

    public synchronized String getConfig(String key) {
        return configMap.get(key);
    }

    public synchronized void removeConfig(String key) {
        configMap.remove(key);
    }
}
