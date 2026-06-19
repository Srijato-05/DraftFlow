package com.draftflow.db;

import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MetadataStore implements AutoCloseable {
    private final Path dbFilePath;
    private MVStore store;
    private MVMap<String, String> indexMap;
    private MVMap<String, String> refMap;
    private MVMap<String, String> changeMap;
    private MVMap<String, String> changeHistoryMap;
    private MVMap<String, String> configMap;

    private final java.util.Map<String, FileMetadata> fileMetadataCache = new java.util.concurrent.ConcurrentHashMap<>();
    private Thread shutdownHook;

    public MetadataStore(Path dbFilePath) {
        this.dbFilePath = dbFilePath;
    }

    public void open() throws IOException {
        Files.createDirectories(dbFilePath.getParent());
        try {
            this.store = new MVStore.Builder()
                    .fileName(dbFilePath.toString())
                    .compress()
                    .open();
        } catch (Exception e) {
            // MVStore corruption recovery: backup corrupt db and recreate a clean one
            Path backupPath = dbFilePath.resolveSibling(dbFilePath.getFileName().toString() + ".corrupted_" + System.currentTimeMillis());
            try {
                if (Files.exists(dbFilePath)) {
                    Files.move(dbFilePath, backupPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ie) {
                try {
                    Files.deleteIfExists(dbFilePath);
                } catch (IOException ignored) {}
            }
            try {
                this.store = new MVStore.Builder()
                        .fileName(dbFilePath.toString())
                        .compress()
                        .open();
            } catch (Exception e2) {
                // Secondary fallback: open in an alternative database file
                Path fallbackPath = dbFilePath.resolveSibling(dbFilePath.getFileName().toString() + ".fallback");
                try {
                    Files.deleteIfExists(fallbackPath);
                } catch (IOException ignored) {}
                this.store = new MVStore.Builder()
                        .fileName(fallbackPath.toString())
                        .compress()
                        .open();
            }
        }
        
        this.indexMap = this.store.openMap("index");
        this.refMap = this.store.openMap("refs");
        this.changeMap = this.store.openMap("changes");
        this.changeHistoryMap = this.store.openMap("changeHistory");
        this.configMap = this.store.openMap("config");

        // Warm up and populate in-memory cache
        this.fileMetadataCache.clear();
        for (Map.Entry<String, String> entry : indexMap.entrySet()) {
            FileMetadata fm = FileMetadata.fromJson(entry.getValue());
            if (fm != null) {
                this.fileMetadataCache.put(entry.getKey(), fm);
            }
        }

        // Graceful VM shutdown hook to commit and close MVStore
        this.shutdownHook = new Thread(() -> {
            try {
                if (store != null && !store.isClosed()) {
                    store.commit();
                    store.close();
                }
            } catch (Exception ex) {
                // Ignore during shutdown
            }
        });
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    public synchronized void commit() {
        if (store != null && !store.isClosed()) {
            store.commit();
        }
    }

    @Override
    public synchronized void close() {
        if (shutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (Exception e) {
                // Ignore if shutdown is already in progress
            }
            shutdownHook = null;
        }
        if (store != null && !store.isClosed()) {
            store.commit();
            store.close();
        }
    }

    // --- Index Cache Operations ---

    public synchronized void putFile(FileMetadata meta) {
        indexMap.put(meta.getPath(), meta.toJson());
        fileMetadataCache.put(meta.getPath(), meta);
    }

    public synchronized FileMetadata getFile(String path) {
        return fileMetadataCache.get(path);
    }

    public synchronized void removeFile(String path) {
        indexMap.remove(path);
        fileMetadataCache.remove(path);
    }

    public synchronized List<FileMetadata> getAllFiles() {
        return new ArrayList<>(fileMetadataCache.values());
    }

    public synchronized void clearIndex() {
        indexMap.clear();
        fileMetadataCache.clear();
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
