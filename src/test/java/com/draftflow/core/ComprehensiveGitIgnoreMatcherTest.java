package com.draftflow.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class ComprehensiveGitIgnoreMatcherTest {

    @TempDir
    Path tempDir;

    @Test
    public void testDefaultExclusions() {
        GitIgnoreMatcher matcher = new GitIgnoreMatcher(tempDir, Collections.emptyList());

        assertTrue(matcher.isIgnored(tempDir.resolve(".draftflow")));
        assertTrue(matcher.isIgnored(tempDir.resolve(".draftflow/config.json")));
        assertTrue(matcher.isIgnored(tempDir.resolve(".git")));
        assertTrue(matcher.isIgnored(tempDir.resolve(".git/HEAD")));
        assertFalse(matcher.isIgnored(tempDir.resolve("main.java")));
    }

    @Test
    public void testConfigExclusions() {
        GitIgnoreMatcher matcher = new GitIgnoreMatcher(tempDir, Arrays.asList("*.log", "target/"));

        assertTrue(matcher.isIgnored(tempDir.resolve("error.log")));
        assertTrue(matcher.isIgnored(tempDir.resolve("sub/dir/activity.log")));
        assertTrue(matcher.isIgnored(tempDir.resolve("target/classes/app.class")));
        assertFalse(matcher.isIgnored(tempDir.resolve("error.txt")));
    }

    @Test
    public void testFileBasedExclusions() throws IOException {
        Path dfIgnore = tempDir.resolve(".dfignore");
        Files.writeString(dfIgnore, "# comment line\n\n*.class\n/tmp/\n");

        GitIgnoreMatcher matcher = new GitIgnoreMatcher(tempDir, Collections.emptyList());

        assertTrue(matcher.isIgnored(tempDir.resolve("app.class")));
        assertTrue(matcher.isIgnored(tempDir.resolve("sub/app.class")));
        assertTrue(matcher.isIgnored(tempDir.resolve("tmp/somefile.txt")));
        assertFalse(matcher.isIgnored(tempDir.resolve("src/tmp/somefile.txt"))); // root-level tmp only
        assertFalse(matcher.isIgnored(tempDir.resolve("app.java")));
    }

    @Test
    public void testCleanPatternsEmpty() {
        GitIgnoreMatcher matcher = new GitIgnoreMatcher(tempDir, Arrays.asList("", "   ", "/"));
        assertFalse(matcher.isIgnored(tempDir.resolve("test.txt")));
    }
}
