package com.draftflow.watcher;

import com.draftflow.core.DraftFlowConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class FSWatcherTest {

    @TempDir
    Path tempDir;

    private FSWatcher watcher;
    private Path watchDir;
    private final Set<Path> triggeredPaths = Collections.synchronizedSet(new HashSet<>());
    private CountDownLatch latch;

    @BeforeEach
    public void setUp() throws IOException {
        watchDir = tempDir.resolve("watch");
        Files.createDirectories(watchDir);
        
        DraftFlowConfig config = new DraftFlowConfig();
        // Exclude a temp folder named "ignored"
        config.getExclude().add("ignored");
        config.getExclude().add(".draftflow");

        latch = new CountDownLatch(1);

        watcher = new FSWatcher(watchDir, config, changedPaths -> {
            triggeredPaths.addAll(changedPaths);
            latch.countDown();
        });
    }

    @AfterEach
    public void tearDown() {
        if (watcher != null) {
            watcher.stop();
        }
    }

    @Test
    public void testWatchEventAndDebounce() throws Exception {
        watcher.start();

        // 1. Create a file inside watched directory
        Path file1 = watchDir.resolve("test.txt");
        Files.writeString(file1, "Initial");

        // 2. Perform multiple quick writes (testing debouncing)
        Files.writeString(file1, "Updated 1");
        Files.writeString(file1, "Updated 2");

        // 3. Create a folder that should be ignored
        Path ignoredDir = watchDir.resolve("ignored");
        Files.createDirectories(ignoredDir);
        Files.writeString(ignoredDir.resolve("should_ignore.txt"), "data");

        // 4. Wait for the debouncer to fire (500ms window)
        boolean fired = latch.await(2, TimeUnit.SECONDS);
        
        // Assertions
        assertTrue(fired, "Watcher callback should have fired after debouncing");
        assertTrue(triggeredPaths.contains(file1), "Callback should include test.txt");
        
        // Excluded files should not be present
        for (Path p : triggeredPaths) {
            assertFalse(p.toString().contains("ignored"), "Should not detect changes in excluded paths");
        }
    }
}
