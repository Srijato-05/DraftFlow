package com.draftflow.core;

import com.draftflow.watcher.FSWatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class ReflogHooksSignatureWatcherTest {

    @TempDir
    Path tempDir;

    // ==========================================
    // SignatureHelper Tests
    // ==========================================

    @Test
    public void testSignatureHelperBasic() throws Exception {
        SignatureHelper.KeyPairStrings keys = SignatureHelper.generateKeyPair();
        assertNotNull(keys.privateKeyBase64);
        assertNotNull(keys.publicKeyBase64);

        byte[] data = "Hello DraftFlow".getBytes(StandardCharsets.UTF_8);
        String signature = SignatureHelper.sign(data, keys.privateKeyBase64);
        assertNotNull(signature);

        assertTrue(SignatureHelper.verify(data, signature, keys.publicKeyBase64));
        assertFalse(SignatureHelper.verify(data, signature + "modified", keys.publicKeyBase64));
        assertFalse(SignatureHelper.verify(data, signature, "invalidPublicKeyBase64"));
    }

    @Test
    public void testSignatureHelperSignRevisionIfKeyExists() throws IOException {
        CAS cas = new CAS(tempDir);
        cas.init();

        Revision rev = new Revision("treeHash", Collections.emptyList(), "changeId", "author", 1000L, "msg", false);
        // Key files don't exist yet
        Revision unsignedRev = SignatureHelper.signRevisionIfKeyExists(rev, cas);
        assertNull(unsignedRev.getSignature());

        // Write invalid keys to trigger Exception handling inside signRevisionIfKeyExists
        Path dfDir = tempDir.resolve(".draftflow");
        Files.writeString(dfDir.resolve("id_ecdsa"), "invalid-key");
        Files.writeString(dfDir.resolve("id_ecdsa.pub"), "invalid-pub-key");

        Revision failedSignRev = SignatureHelper.signRevisionIfKeyExists(rev, cas);
        assertNull(failedSignRev.getSignature());
    }

    // ==========================================
    // ReflogManager Tests
    // ==========================================

    @Test
    public void testReflogManagerBasic() throws IOException {
        // Test normal transition logging
        ReflogManager.logTransition(tempDir, "oldHash", "newHash", "author", "Commit msg");
        List<ReflogManager.ReflogEntry> entries = ReflogManager.getReflog(tempDir);
        assertEquals(1, entries.size());
        assertEquals("oldHash", entries.get(0).getOldHash());
        assertEquals("newHash", entries.get(0).getNewHash());
        assertEquals("author", entries.get(0).getAuthor());
        assertEquals("Commit msg", entries.get(0).getMessage());
        assertTrue(entries.get(0).getTimestamp() > 0);

        // Test transition logging with nulls / defaults
        ReflogManager.logTransition(tempDir, null, "", null, "Second msg");
        List<ReflogManager.ReflogEntry> entries2 = ReflogManager.getReflog(tempDir);
        assertEquals(2, entries2.size());
        assertEquals("0000000000000000000000000000000000000000", entries2.get(1).getOldHash());
        assertEquals("0000000000000000000000000000000000000000", entries2.get(1).getNewHash());
        assertEquals(System.getProperty("user.name"), entries2.get(1).getAuthor());
    }

    @Test
    public void testReflogManagerMalformed() throws IOException {
        Path reflogFile = tempDir.resolve(".draftflow").resolve("logs").resolve("reflog");
        Files.createDirectories(reflogFile.getParent());

        // Write malformed entries to verify parsing edge cases/failures do not crash and cover catch/continue paths
        String malformed = "onlyOneField\n" +
                "two fields\n" +
                "three fields here\n" +
                "four fields no tab 12345\n" +
                "old new author 12345\tvalidMsg\n";

        Files.writeString(reflogFile, malformed);

        List<ReflogManager.ReflogEntry> entries = ReflogManager.getReflog(tempDir);
        assertEquals(1, entries.size());
        assertEquals("validMsg", entries.get(0).getMessage());
        assertEquals(12345L, entries.get(0).getTimestamp());

        // Test NumberFormatException separately to cover the main catch block in ReflogManager
        String numberFormatError = "old new author notANumber\tmsg\n";
        Files.writeString(reflogFile, numberFormatError);
        List<ReflogManager.ReflogEntry> emptyDueToException = ReflogManager.getReflog(tempDir);
        assertTrue(emptyDueToException.isEmpty());

        // Trigger IOException on read by creating a directory at the reflog file path
        Path ioErrorRoot = tempDir.resolve("io-error");
        Path reflogDirPath = ioErrorRoot.resolve(".draftflow").resolve("logs").resolve("reflog");
        Files.createDirectories(reflogDirPath);
        List<ReflogManager.ReflogEntry> empty = ReflogManager.getReflog(ioErrorRoot);
        assertTrue(empty.isEmpty());
    }

    // ==========================================
    // HooksManager Tests
    // ==========================================

    @Test
    public void testHooksManagerSuccess() throws IOException {
        Path hooksDir = tempDir.resolve(".draftflow").resolve("hooks");
        Files.createDirectories(hooksDir);

        // Pre-commit hook that exits with 0
        String scriptContent = System.getProperty("os.name").toLowerCase().contains("win") 
                ? "@echo off\nexit /b 0" 
                : "#!/bin/sh\nexit 0";
        Path hookPath = hooksDir.resolve("pre-commit" + (System.getProperty("os.name").toLowerCase().contains("win") ? ".bat" : ""));
        Files.writeString(hookPath, scriptContent);
        if (!System.getProperty("os.name").toLowerCase().contains("win")) {
            hookPath.toFile().setExecutable(true);
        }

        assertTrue(HooksManager.runHook("pre-commit", tempDir));
    }

    @Test
    public void testHooksManagerFailure() throws IOException {
        Path hooksDir = tempDir.resolve(".draftflow").resolve("hooks");
        Files.createDirectories(hooksDir);

        // Pre-commit hook that exits with 1
        String scriptContent = System.getProperty("os.name").toLowerCase().contains("win") 
                ? "@echo off\nexit /b 1" 
                : "#!/bin/sh\nexit 1";
        Path hookPath = hooksDir.resolve("pre-commit" + (System.getProperty("os.name").toLowerCase().contains("win") ? ".bat" : ""));
        Files.writeString(hookPath, scriptContent);
        if (!System.getProperty("os.name").toLowerCase().contains("win")) {
            hookPath.toFile().setExecutable(true);
        }

        assertFalse(HooksManager.runHook("pre-commit", tempDir));
    }

    @Test
    public void testHooksManagerNoHook() {
        // No hook files exist
        assertTrue(HooksManager.runHook("non-existent-hook", tempDir));
    }

    // ==========================================
    // FSWatcher Tests
    // ==========================================

    @Test
    public void testFSWatcherEdgeCases() throws Exception {
        DraftFlowConfig config = new DraftFlowConfig();
        config.getExclude().add("*.ignored");

        CountDownLatch latch = new CountDownLatch(1);
        Set<Path> receivedChanges = new HashSet<>();

        FSWatcher watcher = new FSWatcher(tempDir, config, changedPaths -> {
            receivedChanges.addAll(changedPaths);
            latch.countDown();
        });

        // 1. Double start
        watcher.start();
        watcher.start(); // Should be a no-op

        // Write a file
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "content");

        // Write an excluded file
        Path excludedFile = tempDir.resolve("test.ignored");
        Files.writeString(excludedFile, "ignored content");

        // Wait for debounce and notification
        latch.await(3, TimeUnit.SECONDS);

        assertTrue(receivedChanges.contains(file));
        assertFalse(receivedChanges.contains(excludedFile));

        // 2. Stop and double stop
        watcher.stop();
        watcher.stop(); // Should be a no-op
    }
}
