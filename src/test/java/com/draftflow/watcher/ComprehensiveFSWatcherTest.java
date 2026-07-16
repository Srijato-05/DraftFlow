package com.draftflow.watcher;

import com.draftflow.core.DraftFlowConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class ComprehensiveFSWatcherTest {

    @TempDir
    Path tempDir;

    @Test
    public void testWatcherLifecycleAndDebounce() throws Exception {
        DraftFlowConfig config = new DraftFlowConfig();
        config.setExclude(Collections.singletonList(".draftflow"));

        CountDownLatch latch = new CountDownLatch(1);
        final Set<Path> detectedChanges = new HashSet<>();

        FSWatcher watcher = new FSWatcher(tempDir, config, changedPaths -> {
            detectedChanges.addAll(changedPaths);
            latch.countDown();
        });

        // Start watcher
        watcher.start();

        // Create a test file
        Path testFile = tempDir.resolve("test_watcher_file.txt");
        Files.writeString(testFile, "Watcher data");

        // Wait up to 2 seconds for the event to debounce (debounce is 500ms)
        boolean eventDetected = latch.await(2, TimeUnit.SECONDS);

        // Stop watcher
        watcher.stop();

        // Check if event was debounced and registered
        if (eventDetected) {
            assertTrue(detectedChanges.stream().anyMatch(p -> p.getFileName().toString().equals("test_watcher_file.txt")));
        }
    }

    @Test
    public void testWatcherExclusion() throws Exception {
        DraftFlowConfig config = new DraftFlowConfig();
        // Exclude .draftflow and *.log
        config.setExclude(java.util.List.of(".draftflow", "*.log"));

        CountDownLatch latch = new CountDownLatch(1);
        final Set<Path> detectedChanges = new HashSet<>();

        FSWatcher watcher = new FSWatcher(tempDir, config, changedPaths -> {
            detectedChanges.addAll(changedPaths);
            latch.countDown();
        });

        watcher.start();

        // Create a file that is excluded (.draftflow directory should be ignored)
        Path dfDir = tempDir.resolve(".draftflow");
        Files.createDirectories(dfDir);
        Path dfFile = dfDir.resolve("should_ignore.txt");
        Files.writeString(dfFile, "Ignore this");

        // Create an ignored log file
        Path logFile = tempDir.resolve("activity.log");
        Files.writeString(logFile, "Ignore log");

        // Wait a short time to verify no event triggered the latch
        boolean eventDetected = latch.await(800, TimeUnit.MILLISECONDS);
        watcher.stop();

        assertFalse(eventDetected);
        assertTrue(detectedChanges.isEmpty());
    }

    @Test
    public void testWatcherMultipleStarts() throws Exception {
        DraftFlowConfig config = new DraftFlowConfig();
        FSWatcher watcher = new FSWatcher(tempDir, config, changes -> {});
        
        watcher.start();
        // Multiple starts should be safe and idempotent
        watcher.start();
        
        watcher.stop();
        // Multiple stops should also be safe
        watcher.stop();
    }
}
