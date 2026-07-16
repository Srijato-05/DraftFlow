package com.draftflow.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ComprehensiveReflogManagerTest {

    @TempDir
    Path tempDir;

    @Test
    public void testLogTransitionAndRetrieve() {
        String oldHash = "0000000000000000000000000000000000000000";
        String newHash = "abcdef1234567890abcdef1234567890abcdef12";
        String author = "Author Name <author@example.com>";
        String message = "Initial commit";

        ReflogManager.logTransition(tempDir, oldHash, newHash, author, message);

        List<ReflogManager.ReflogEntry> entries = ReflogManager.getReflog(tempDir);
        assertEquals(1, entries.size());
        ReflogManager.ReflogEntry entry = entries.get(0);

        assertEquals(oldHash, entry.getOldHash());
        assertEquals(newHash, entry.getNewHash());
        assertEquals(author, entry.getAuthor());
        assertEquals(message, entry.getMessage());
        assertTrue(entry.getTimestamp() > 0);
    }

    @Test
    public void testMissingReflogFile() {
        List<ReflogManager.ReflogEntry> entries = ReflogManager.getReflog(tempDir.resolve("nonexistent"));
        assertTrue(entries.isEmpty());
    }

    @Test
    public void testNullAndEmptyInputs() {
        ReflogManager.logTransition(tempDir, null, "", null, "");

        List<ReflogManager.ReflogEntry> entries = ReflogManager.getReflog(tempDir);
        assertEquals(1, entries.size());
        ReflogManager.ReflogEntry entry = entries.get(0);

        assertEquals("0000000000000000000000000000000000000000", entry.getOldHash());
        assertEquals("0000000000000000000000000000000000000000", entry.getNewHash());
        assertNotNull(entry.getAuthor());
        assertFalse(entry.getAuthor().isEmpty());
        assertEquals("", entry.getMessage());
    }

    @Test
    public void testParseMalformedLines() throws Exception {
        Path reflogFile = tempDir.resolve(".draftflow").resolve("logs").resolve("reflog");
        Files.createDirectories(reflogFile.getParent());

        // Write some malformed lines along with a well-formed one
        String content = "invalidline\n" +
                "hash1 hash2 Srijato\n" + // missing tab and timestamp
                "hash1 hash2 Srijato 12345\n" + // missing tab
                "hash1 hash2 Srijato 12345\tvalid message\n"; // valid line

        Files.writeString(reflogFile, content, StandardCharsets.UTF_8);

        List<ReflogManager.ReflogEntry> entries = ReflogManager.getReflog(tempDir);
        assertEquals(1, entries.size());
        assertEquals("valid message", entries.get(0).getMessage());
        assertEquals("Srijato", entries.get(0).getAuthor());
        assertEquals(12345L, entries.get(0).getTimestamp());
    }

    @Test
    public void testMultipleTransitionsOrder() {
        ReflogManager.logTransition(tempDir, "hash0", "hash1", "user", "msg1");
        ReflogManager.logTransition(tempDir, "hash1", "hash2", "user", "msg2");

        List<ReflogManager.ReflogEntry> entries = ReflogManager.getReflog(tempDir);
        assertEquals(2, entries.size());
        assertEquals("msg1", entries.get(0).getMessage());
        assertEquals("msg2", entries.get(1).getMessage());
    }
}
