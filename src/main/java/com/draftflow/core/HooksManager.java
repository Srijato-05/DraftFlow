package com.draftflow.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class HooksManager {

    /**
     * Runs a hook script if it exists in .draftflow/hooks.
     * Returns true if the hook executed successfully (exit code 0) or did not exist.
     * Returns false if the hook failed (non-zero exit code or execution error).
     */
    public static boolean runHook(String hookName, Path repoRoot, String... args) {
        Path hooksDir = repoRoot.resolve(".draftflow").resolve("hooks");
        Path hookPath = hooksDir.resolve(hookName);

        // Check for common extensions on Windows if the base name doesn't exist
        if (!Files.exists(hookPath) && System.getProperty("os.name").toLowerCase().contains("win")) {
            if (Files.exists(hooksDir.resolve(hookName + ".bat"))) {
                hookPath = hooksDir.resolve(hookName + ".bat");
            } else if (Files.exists(hooksDir.resolve(hookName + ".cmd"))) {
                hookPath = hooksDir.resolve(hookName + ".cmd");
            }
        }

        if (!Files.exists(hookPath)) {
            return true; // Skip if hook doesn't exist
        }

        try {
            List<String> command = new ArrayList<>();
            boolean isWin = System.getProperty("os.name").toLowerCase().contains("win");
            
            if (isWin) {
                command.add("cmd.exe");
                command.add("/c");
                command.add(hookPath.toAbsolutePath().toString());
            } else {
                command.add(hookPath.toAbsolutePath().toString());
            }

            for (String arg : args) {
                command.add(arg);
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(repoRoot.toFile());
            
            // Redirect output to inherit so the user/developer sees the output in their terminal
            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);

            Process p = pb.start();
            int exitCode = p.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            System.err.println("Warning: Hook '" + hookName + "' failed to execute: " + e.getMessage());
            return false;
        }
    }
}
