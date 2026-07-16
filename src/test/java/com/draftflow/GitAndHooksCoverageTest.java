package com.draftflow;

import com.draftflow.core.HooksManager;
import picocli.CommandLine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class GitAndHooksCoverageTest {

    static {
        System.setProperty("java.awt.headless", "true");
    }

    @TempDir
    Path tempDir;

    private Path repoDir;
    private String originalDraftFlowDir;
    private String originalOsName;
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private PrintStream originalOut;
    private PrintStream originalErr;

    @BeforeEach
    public void setUp() throws IOException {
        repoDir = tempDir.resolve("coverage-repo").toAbsolutePath().normalize();
        Files.createDirectories(repoDir);
        originalDraftFlowDir = System.getProperty("draftflow.dir");
        System.setProperty("draftflow.dir", repoDir.toString());

        originalOsName = System.getProperty("os.name");

        originalOut = System.out;
        originalErr = System.err;

        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    public void tearDown() {
        if (originalDraftFlowDir != null) {
            System.setProperty("draftflow.dir", originalDraftFlowDir);
        } else {
            System.clearProperty("draftflow.dir");
        }
        if (originalOsName != null) {
            System.setProperty("os.name", originalOsName);
        }
        originalOut.println("=== TEST STDOUT ===");
        originalOut.print(outContent.toString());
        originalOut.println("===================");
        originalErr.println("=== TEST STDERR ===");
        originalErr.print(errContent.toString());
        originalErr.println("===================");
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    private int runCommand(String... args) {
        outContent.reset();
        errContent.reset();
        return new CommandLine(new DraftFlow()).execute(args);
    }

    @Test
    public void testGitImportFatalNotARepo() {
        int code = runCommand("git-import", "some-path");
        assertEquals(1, code);
        assertTrue(errContent.toString().contains("Fatal: Not a draftflow repository."));
    }

    @Test
    public void testGitImportFatalNotGitRepo() {
        int code = runCommand("setup");
        assertEquals(0, code);

        Path notGitDir = tempDir.resolve("not-git-dir");
        int importCode = runCommand("git-import", notGitDir.toString());
        assertEquals(1, importCode);
        assertTrue(errContent.toString().contains("is not a valid Git repository"));
    }

    @Test
    public void testGitImportEmptyGitRepo() throws Exception {
        int code = runCommand("setup");
        assertEquals(0, code);

        Path gitDir = tempDir.resolve("empty-git-repo");
        Files.createDirectories(gitDir);
        com.draftflow.DraftFlow.runProcess(gitDir, "git", "init");

        // We also need to configure user.name and user.email for the temporary repo
        com.draftflow.DraftFlow.runProcess(gitDir, "git", "config", "user.name", "Test User");
        com.draftflow.DraftFlow.runProcess(gitDir, "git", "config", "user.email", "test@example.com");

        int importCode = runCommand("git-import", gitDir.toString());
        assertEquals(0, importCode);
        assertTrue(outContent.toString().contains("No commits found to import."));
    }

    @Test
    public void testGitImportSuccess() throws Exception {
        int code = runCommand("setup");
        assertEquals(0, code);

        // Create keys so revision signature logic is covered
        runCommand("keys");

        Path gitDir = tempDir.resolve("active-git-repo");
        Files.createDirectories(gitDir);
        com.draftflow.DraftFlow.runProcess(gitDir, "git", "init");
        com.draftflow.DraftFlow.runProcess(gitDir, "git", "config", "user.name", "Git Author");
        com.draftflow.DraftFlow.runProcess(gitDir, "git", "config", "user.email", "author@git.com");

        // Commit 1
        Path f1 = gitDir.resolve("file1.txt");
        Files.writeString(f1, "content1");
        com.draftflow.DraftFlow.runProcess(gitDir, "git", "add", "file1.txt");
        com.draftflow.DraftFlow.runProcess(gitDir, "git", "commit", "-m", "commit number one");

        // Commit 2
        Path f2 = gitDir.resolve("file2.txt");
        Files.writeString(f2, "content2");
        com.draftflow.DraftFlow.runProcess(gitDir, "git", "add", "file2.txt");
        com.draftflow.DraftFlow.runProcess(gitDir, "git", "commit", "-m", "commit number two");

        int importCode = runCommand("git-import", gitDir.toString());
        assertEquals(0, importCode);
        assertTrue(outContent.toString().contains("Importing 2 commits from Git repository"));

        // Verify commits are in DraftFlow
        int historyCode = runCommand("history");
        assertEquals(0, historyCode);
        String historyOut = outContent.toString();
        assertTrue(historyOut.contains("commit number one"));
        assertTrue(historyOut.contains("commit number two"));
    }

    @Test
    public void testGitExportFatalNotARepo() {
        int code = runCommand("git-export", "some-path");
        assertEquals(1, code);
        assertTrue(errContent.toString().contains("Fatal: Not a draftflow repository."));
    }

    @Test
    public void testGitExportEmptyDraftflowRepo() {
        int code = runCommand("setup");
        assertEquals(0, code);

        Path exportDir = tempDir.resolve("export-target-empty");
        int exportCode = runCommand("git-export", exportDir.toString());
        assertEquals(0, exportCode);
        assertTrue(outContent.toString().contains("Nothing to export"));
    }

    @Test
    public void testGitExportSuccess() throws Exception {
        int code = runCommand("setup");
        assertEquals(0, code);

        // Create keys
        runCommand("keys");

        // Create some commits in DraftFlow
        Path file1 = repoDir.resolve("file1.txt");
        Files.writeString(file1, "draftflow data 1");
        int save1 = runCommand("save", "-m", "df commit 1");
        assertEquals(0, save1);

        Path file2 = repoDir.resolve("file2.txt");
        Files.writeString(file2, "draftflow data 2");
        int save2 = runCommand("save", "-m", "df commit 2");
        assertEquals(0, save2);

        Path exportDir = tempDir.resolve("export-target-success");
        int exportCode = runCommand("git-export", exportDir.toString());
        assertEquals(0, exportCode);

        // Verify git commits exist in the target
        assertTrue(Files.exists(exportDir.resolve(".git")));
        String logOutput = com.draftflow.DraftFlow.runProcess(exportDir, "git", "log", "--oneline");
        System.out.println("=== EXPORT LOG OUTPUT ===");
        System.out.println(logOutput);
        System.out.println("=========================");
        assertTrue(logOutput.contains("df commit 1"));
        assertTrue(logOutput.contains("df commit 2"));
    }

    @Test
    public void testHooksFatalNotARepo() {
        int code = runCommand("hooks");
        assertEquals(1, code);
        assertTrue(errContent.toString().contains("Fatal: Not a draftflow repository."));
    }

    @Test
    public void testHooksListDefault() {
        int code = runCommand("setup");
        assertEquals(0, code);

        int hooksCode = runCommand("hooks");
        assertEquals(0, hooksCode);
        assertTrue(outContent.toString().contains("DraftFlow Repository Hooks Status:"));
    }

    @Test
    public void testHooksCreateSampleWindows() {
        int code = runCommand("setup");
        assertEquals(0, code);

        System.setProperty("os.name", "Windows 11");
        int hooksCode = runCommand("hooks", "--create-sample");
        assertEquals(0, hooksCode);
        assertTrue(outContent.toString().contains("Created sample batch script"));
        assertTrue(Files.exists(repoDir.resolve(".draftflow").resolve("hooks").resolve("pre-commit.bat")));
    }

    @Test
    public void testHooksCreateSampleNonWindows() {
        int code = runCommand("setup");
        assertEquals(0, code);

        System.setProperty("os.name", "Linux");
        int hooksCode = runCommand("hooks", "--create-sample");
        assertEquals(0, hooksCode);
        assertTrue(outContent.toString().contains("Created sample shell script"));
        assertTrue(Files.exists(repoDir.resolve(".draftflow").resolve("hooks").resolve("pre-commit")));
    }

    @Test
    public void testHooksInstallAndFailures() throws IOException {
        int code = runCommand("setup");
        assertEquals(0, code);

        Path tempScript = tempDir.resolve("my-script.bat");
        Files.writeString(tempScript, "@echo off\necho Installed!");

        // 1. Script path does not exist
        int installCode1 = runCommand("hooks", "--install", "pre-commit", "--script", tempDir.resolve("nonexistent.bat").toString());
        assertEquals(1, installCode1);
        assertTrue(errContent.toString().contains("Script file does not exist"));

        // 2. Invalid hook name
        int installCode2 = runCommand("hooks", "--install", "invalid-hook-name", "--script", tempScript.toString());
        assertEquals(1, installCode2);
        assertTrue(errContent.toString().contains("Invalid hook name"));

        // 3. Valid hook install (Windows)
        System.setProperty("os.name", "Windows 11");
        int installCode3 = runCommand("hooks", "--install", "pre-commit", "--script", tempScript.toString());
        assertEquals(0, installCode3);
        assertTrue(outContent.toString().contains("Successfully installed script as hook"));
        assertTrue(Files.exists(repoDir.resolve(".draftflow").resolve("hooks").resolve("pre-commit.bat")));

        // 4. Valid hook install (Linux/Mac)
        System.setProperty("os.name", "Linux");
        Path tempSh = tempDir.resolve("my-script.sh");
        Files.writeString(tempSh, "#!/bin/sh\necho Installed!");
        int installCode4 = runCommand("hooks", "--install", "pre-commit", "--script", tempSh.toString());
        assertEquals(0, installCode4);
        assertTrue(outContent.toString().contains("Successfully installed script as hook"));
        assertTrue(Files.exists(repoDir.resolve(".draftflow").resolve("hooks").resolve("pre-commit")));
    }

    @Test
    public void testHooksManagerExecution() throws IOException {
        // HooksManager.runHook with non-existent hook should skip and return true
        assertTrue(HooksManager.runHook("pre-commit", repoDir));

        // Create hooks directory
        Path hooksDir = repoDir.resolve(".draftflow").resolve("hooks");
        Files.createDirectories(hooksDir);

        // Windows execution check
        System.setProperty("os.name", "Windows 11");
        
        // 1. Batch file returning 0
        Path batOk = hooksDir.resolve("pre-commit.bat");
        Files.writeString(batOk, "@echo off\nexit /b 0");
        assertTrue(HooksManager.runHook("pre-commit", repoDir));

        // 2. Cmd file returning 1
        Path cmdFail = hooksDir.resolve("pre-commit.cmd");
        Files.writeString(cmdFail, "@echo off\nexit /b 1");
        // Delete pre-commit.bat to ensure cmd is picked up
        Files.deleteIfExists(batOk);
        assertFalse(HooksManager.runHook("pre-commit", repoDir));

        // Linux execution check
        System.setProperty("os.name", "Linux");
        Path shOk = hooksDir.resolve("post-commit");
        Files.writeString(shOk, "#!/bin/sh\nexit 0");
        shOk.toFile().setExecutable(true);
        assertTrue(HooksManager.runHook("post-commit", repoDir));

        Path shFail = hooksDir.resolve("post-commit-fail");
        Files.writeString(shFail, "#!/bin/sh\nexit 1");
        shFail.toFile().setExecutable(true);
        assertFalse(HooksManager.runHook("post-commit-fail", repoDir));

        // Exception handling: pass a non-existent repoRoot directory to trigger Exception in ProcessBuilder.start()
        Path nonExistent = tempDir.resolve("nonexistent-dir");
        assertFalse(HooksManager.runHook("post-commit", nonExistent));
    }
}
