package com.draftflow.watcher;

import com.draftflow.core.DraftFlowConfig;
import com.draftflow.core.GitIgnoreMatcher;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;

public class FSWatcher {
    public interface WatcherListener {
        void onFilesChanged(Set<Path> changedPaths);
    }

    private final Path rootDir;
    private final WatcherListener listener;
    private final WatchService watchService;
    private final Map<WatchKey, Path> watchKeys = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "draftflow-watcher-scheduler");
        t.setDaemon(true);
        return t;
    });
    
    private final Set<Path> pendingChanges = Collections.synchronizedSet(new HashSet<>());
    private ScheduledFuture<?> debounceTask;
    private Thread watcherThread;
    private volatile boolean running = false;
    private final GitIgnoreMatcher ignoreMatcher;

    public FSWatcher(Path rootDir, DraftFlowConfig config, WatcherListener listener) throws IOException {
        this.rootDir = rootDir;
        this.listener = listener;
        this.watchService = FileSystems.getDefault().newWatchService();
        this.ignoreMatcher = new GitIgnoreMatcher(rootDir, config.getExclude());
    }

    public void start() throws IOException {
        if (running) return;
        running = true;

        // Register root and all child directories
        registerAll(rootDir);

        watcherThread = new Thread(this::watchLoop, "draftflow-watcher-thread");
        watcherThread.setDaemon(true);
        watcherThread.start();
    }

    public void stop() {
        if (!running) return;
        running = false;

        try {
            watchService.close();
        } catch (IOException e) {
            // Ignore
        }

        scheduler.shutdownNow();
        if (watcherThread != null) {
            watcherThread.interrupt();
        }
    }

    private void registerAll(Path start) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (isExcluded(dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                registerDirectory(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void registerDirectory(Path dir) throws IOException {
        WatchKey key = dir.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY
        );
        watchKeys.put(key, dir);
    }

    private boolean isExcluded(Path path) {
        return ignoreMatcher.isIgnored(path);
    }

    private void watchLoop() {
        while (running) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException | ClosedWatchServiceException e) {
                break;
            }

            Path dir = watchKeys.get(key);
            if (dir == null) {
                key.cancel();
                continue;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                WatchEvent<Path> ev = (WatchEvent<Path>) event;
                Path filename = ev.context();
                Path child = dir.resolve(filename);

                if (isExcluded(child)) {
                    continue;
                }

                // If a new directory is created, register it recursively and scan pre-existing contents
                if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                    try {
                        if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                            registerAll(child);
                            
                            // Walk and add all children of the newly created folder to pendingChanges
                            Files.walkFileTree(child, new SimpleFileVisitor<>() {
                                @Override
                                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                    if (!isExcluded(file)) {
                                        pendingChanges.add(file);
                                    }
                                    return FileVisitResult.CONTINUE;
                                }
                            });
                        }
                    } catch (IOException e) {
                        System.err.println("Failed to dynamically register directory: " + child + " : " + e.getMessage());
                    }
                }

                // Add to pending changes
                pendingChanges.add(child);
                triggerDebounce();
            }

            boolean valid = key.reset();
            if (!valid) {
                watchKeys.remove(key);
                if (watchKeys.isEmpty()) {
                    // All directories watched are lost
                    break;
                }
            }
        }
    }

    private synchronized void triggerDebounce() {
        if (debounceTask != null && !debounceTask.isDone()) {
            debounceTask.cancel(false);
        }
        debounceTask = scheduler.schedule(this::notifyListener, 500, TimeUnit.MILLISECONDS);
    }

    private void notifyListener() {
        Set<Path> changes;
        synchronized (pendingChanges) {
            changes = new HashSet<>(pendingChanges);
            pendingChanges.clear();
        }
        if (!changes.isEmpty()) {
            listener.onFilesChanged(changes);
        }
    }
}
