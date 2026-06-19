package com.draftflow;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class DraftFlowCLITest {

    @TempDir
    Path tempDir;

    private String originalDraftFlowDirProp;
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @BeforeEach
    public void setUp() {
        originalDraftFlowDirProp = System.getProperty("draftflow.dir");
        System.setProperty("draftflow.dir", tempDir.toAbsolutePath().toString());
        
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    public void tearDown() {
        if (originalDraftFlowDirProp != null) {
            System.setProperty("draftflow.dir", originalDraftFlowDirProp);
        } else {
            System.clearProperty("draftflow.dir");
        }
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    public void testCliLifecycle() throws IOException {
        CommandLine cmd = new CommandLine(new DraftFlow());

        // 1. Run "setup"
        int initCode = cmd.execute("setup");
        assertEquals(0, initCode);
        assertTrue(Files.exists(tempDir.resolve(".draftflow")));

        // 2. Check "status" on empty repo
        outContent.reset();
        int statusCode = cmd.execute("status");
        assertEquals(0, statusCode);
        String statusOutput = outContent.toString();
        assertTrue(statusOutput.contains("On branch: main"));
        assertTrue(statusOutput.contains("Working copy is clean."));

        // 3. Create a file and "save"
        Path file = tempDir.resolve("hello.txt");
        Files.writeString(file, "Hello CLI");

        int commitCode = cmd.execute("save", "-m", "Initial CLI Commit");
        assertEquals(0, commitCode);

        // 4. Modify file and check "status"
        Files.writeString(file, "Hello CLI Modified");
        outContent.reset();
        cmd.execute("status");
        statusOutput = outContent.toString();
        assertTrue(statusOutput.contains("Modified files:"));
        assertTrue(statusOutput.contains("hello.txt"));

        // 5. Save modification
        int commitCode2 = cmd.execute("save", "-m", "Second CLI Commit");
        assertEquals(0, commitCode2);

        // 6. Check status is clean again
        outContent.reset();
        cmd.execute("status");
        assertTrue(outContent.toString().contains("Working copy is clean."));
    }

    @TempDir
    Path remoteDir;

    @Test
    public void testCliExtendedCommands() throws IOException {
        CommandLine cmd = new CommandLine(new DraftFlow());

        // 1. Setup
        cmd.execute("setup");

        // 2. Save a file
        Path file = tempDir.resolve("hello.txt");
        Files.writeString(file, "Hello CLI");
        cmd.execute("save", "-m", "Commit 1");

        // 3. Create branch
        int branchCode = cmd.execute("branch", "-c", "dev");
        assertEquals(0, branchCode);

        // List branches
        outContent.reset();
        cmd.execute("branch");
        String branchList = outContent.toString();
        assertTrue(branchList.contains("dev"));
        assertTrue(branchList.contains("main"));

        // 4. History
        outContent.reset();
        int logCode = cmd.execute("history");
        assertEquals(0, logCode);
        assertTrue(outContent.toString().contains("Commit 1"));

        // 5. Save second file to verify Undo
        Path file2 = tempDir.resolve("world.txt");
        Files.writeString(file2, "World CLI");
        cmd.execute("save", "-m", "Commit 2");

        // Undo
        int undoCode = cmd.execute("undo");
        assertEquals(0, undoCode);

        // Verify world.txt is removed after undo
        assertFalse(Files.exists(file2));

        // 6. Upload
        String remoteUrl = "file://" + remoteDir.toAbsolutePath().toString().replace("\\", "/");
        int pushCode = cmd.execute("upload", "--remote", remoteUrl);
        assertEquals(0, pushCode);

        // 7. Download back in another directory
        Path pullDir = tempDir.resolve("pull_dir");
        Files.createDirectories(pullDir);

        // Switch active directory property to pullDir
        System.setProperty("draftflow.dir", pullDir.toAbsolutePath().toString());
        CommandLine cmd2 = new CommandLine(new DraftFlow());
        cmd2.execute("setup");

        int pullCode = cmd2.execute("download", "--remote", remoteUrl);
        assertEquals(0, pullCode);
        assertTrue(Files.exists(pullDir.resolve("hello.txt")));
        assertEquals("Hello CLI", Files.readString(pullDir.resolve("hello.txt")));
    }
}
