package com.draftflow.watcher;

import com.draftflow.core.DraftFlowConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class AdvancedFSWatcherTest {

    @TempDir
    Path tempDir;

    private FSWatcher watcher;
    private DraftFlowConfig config;
    private final List<Set<Path>> notifications = new CopyOnWriteArrayList<>();
    private final CountDownLatch latch = new CountDownLatch(1);

    @BeforeEach
    public void setUp() throws IOException {
        Path configPath = tempDir.resolve("config.json");
        Files.writeString(configPath, "{\n  \"version\": \"1.0\",\n  \"hashAlgorithm\": \"SHA-256\",\n  \"exclude\": [\".draftflow\"]\n}");
        config = DraftFlowConfig.load(configPath);
    }

    @AfterEach
    public void tearDown() {
        if (watcher != null) {
            watcher.stop();
        }
    }

    @Test
    public void testIntenseDebouncingCoalescence() throws Exception {
        watcher = new FSWatcher(tempDir, config, paths -> {
            notifications.add(paths);
            latch.countDown();
        });
        watcher.start();

        Path testFile = tempDir.resolve("debounce_test.txt");

        // Fire 30 writes spaced by 30ms (total time ~900ms)
        // Debounce is 500ms, so it should keep resetting and only notify once at the end
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 30; i++) {
            Files.writeString(testFile, "Write iteration: " + i);
            Thread.sleep(30);
        }

        // Wait for notification to fire (writes took ~900ms + debounce 500ms + buffer)
        boolean notified = latch.await(2000, TimeUnit.MILLISECONDS);
        assertTrue(notified, "Watcher failed to notify listener");

        // Wait a bit more to ensure no extra false notifications occur
        Thread.sleep(300);

        assertEquals(1, notifications.size(), "All rapid writes must coalesce into a single notification event");
        assertTrue(notifications.get(0).contains(testFile));
        
        long elapsed = System.currentTimeMillis() - startTime;
        assertTrue(elapsed >= 1400, "Notification should occur after the write sequence (900ms) plus the debounce window (500ms). Elapsed: " + elapsed + "ms");
    }

    @Test
    public void testDynamicRecursiveRegistration() throws Exception {
        CountDownLatch dynamicLatch = new CountDownLatch(1);
        List<Set<Path>> dynamicNotifications = new CopyOnWriteArrayList<>();

        watcher = new FSWatcher(tempDir, config, paths -> {
            dynamicNotifications.add(paths);
            dynamicLatch.countDown();
        });
        watcher.start();

        // 1. Dynamically create level 1 nested directory
        Path level1 = tempDir.resolve("nested1");
        Files.createDirectory(level1);

        // Give the watcher a brief moment to process the dynamic ENTRY_CREATE on level 1
        Thread.sleep(150);

        // 2. Create level 2 nested directory inside level 1
        Path level2 = level1.resolve("nested2");
        Files.createDirectory(level2);

        // Give watcher moment to register level 2
        Thread.sleep(150);

        // 3. Write a file inside level 2
        Path nestedFile = level2.resolve("target.txt");
        Files.writeString(nestedFile, "Dynamic deep write");

        // Wait for debounce and notification
        boolean notified = dynamicLatch.await(1500, TimeUnit.MILLISECONDS);
        assertTrue(notified, "Watcher failed to notify for the dynamically registered nested file");

        // Check if the deeply nested file is in the notifications
        boolean found = false;
        for (Set<Path> set : dynamicNotifications) {
            if (set.contains(nestedFile)) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Nested file notification was not found in the received notifications");
    }
}
