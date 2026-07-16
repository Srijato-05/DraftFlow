/**
 * @file ReflogManager.java
 * @description The Reflog manager for the DraftFlow VCS.
 * Records reference log entries in `.draftflow/logs/` directories to track updates to reference pointers (branches/HEADs).
 * Provides the transactional audit history that power the "Reflog Ledger" panel in the UI.
 * 
 * DESIGN RATIONALE:
 * - Stores transaction logs in standard text-append format to prevent locking issues.
 * - Parses reflog streams linearly to recover old reference hashes, serving as an undo system for branch deletes/resets.
 */

package com.draftflow.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ReflogManager {

    public static class ReflogEntry {
        private final String oldHash;
        private final String newHash;
        private final String author;
        private final long timestamp;
        private final String message;

        public ReflogEntry(String oldHash, String newHash, String author, long timestamp, String message) {
            this.oldHash = oldHash;
            this.newHash = newHash;
            this.author = author;
            this.timestamp = timestamp;
            this.message = message;
        }

        public String getOldHash() {
            return oldHash;
        }

        public String getNewHash() {
            return newHash;
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
    }

    /**
     * Appends an entry to the global reflog log.
     */
    public static void logTransition(Path repoRoot, String oldHash, String newHash, String author, String message) {
        Path reflogFile = repoRoot.resolve(".draftflow").resolve("logs").resolve("reflog");
        try {
            Files.createDirectories(reflogFile.getParent());
            String safeOld = (oldHash == null || oldHash.isEmpty()) ? "0000000000000000000000000000000000000000" : oldHash;
            String safeNew = (newHash == null || newHash.isEmpty()) ? "0000000000000000000000000000000000000000" : newHash;
            String safeAuthor = (author == null || author.isEmpty()) ? System.getProperty("user.name") : author;
            
            String line = String.format("%s %s %s %d\t%s\n", safeOld, safeNew, safeAuthor, System.currentTimeMillis(), message);
            Files.writeString(reflogFile, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Warning: Failed to write reflog: " + e.getMessage());
        }
    }

    /**
     * Reads all reflog entries from the reflog file.
     */
    public static List<ReflogEntry> getReflog(Path repoRoot) {
        Path reflogFile = repoRoot.resolve(".draftflow").resolve("logs").resolve("reflog");
        if (!Files.exists(reflogFile)) {
            return Collections.emptyList();
        }

        List<ReflogEntry> entries = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(reflogFile, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                int firstSpace = line.indexOf(' ');
                if (firstSpace == -1) continue;
                int secondSpace = line.indexOf(' ', firstSpace + 1);
                if (secondSpace == -1) continue;
                int tabIndex = line.indexOf('\t');
                if (tabIndex == -1) continue;
                int spaceBeforeTimestamp = line.lastIndexOf(' ', tabIndex - 1);
                if (spaceBeforeTimestamp == -1 || spaceBeforeTimestamp <= secondSpace) continue;

                String oldHash = line.substring(0, firstSpace);
                String newHash = line.substring(firstSpace + 1, secondSpace);
                String author = line.substring(secondSpace + 1, spaceBeforeTimestamp);
                long timestamp = Long.parseLong(line.substring(spaceBeforeTimestamp + 1, tabIndex).trim());
                String message = line.substring(tabIndex + 1);

                entries.add(new ReflogEntry(oldHash, newHash, author, timestamp, message));
            }
        } catch (Exception e) {
            System.err.println("Warning: Failed to read reflog: " + e.getMessage());
        }
        return entries;
    }
}
