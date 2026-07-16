/**
 * @file LFSManager.java
 * @description Large File Storage (LFS) manager for DraftFlow VCS.
 * Manages tracking, pointer generation, and cache extraction of files exceeding the size threshold
 * or matching specific media extensions.
 * 
 * DESIGN RATIONALE:
 * - Large media, model, or binary files bloat the main Content Addressable Storage (CAS) object repository,
 *   causing performance issues.
 * - Instead of storing raw data, LFS creates a tiny textual "pointer file" in the CAS pointing to the actual binary
 *   which is stored in a separate shard database under `.draftflow/lfs/` named by its SHA-256 hash.
 */

package com.draftflow.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class LFSManager {

    public static boolean isLfsFile(Path file, DraftFlowConfig config) {
        try {
            long size = Files.size(file);
            long threshold = (config.getLfsSizeThreshold() != null) ? config.getLfsSizeThreshold() : 10L * 1024 * 1024;
            if (size >= threshold) {
                return true;
            }
            List<String> exts = config.getLfsExtensions();
            if (exts != null) {
                String name = file.getFileName().toString().toLowerCase();
                for (String ext : exts) {
                    if (name.endsWith(ext.toLowerCase())) {
                        return true;
                    }
                }
            }
        } catch (IOException e) {
            // ignore
        }
        return false;
    }

    public static String createLfsPointer(Path repoRoot, Path srcFile) throws IOException {
        byte[] fileBytes = Files.readAllBytes(srcFile);
        String sha256 = Hasher.hash(fileBytes);
        long size = Files.size(srcFile);

        // Store in local LFS cache
        Path lfsCache = repoRoot.resolve(".draftflow").resolve("lfs");
        Path targetDir = lfsCache.resolve(sha256.substring(0, 2));
        Files.createDirectories(targetDir);
        Path targetFile = targetDir.resolve(sha256.substring(2));
        Files.copy(srcFile, targetFile, StandardCopyOption.REPLACE_EXISTING);

        return "version draftflow-lfs/v1\noid sha256:" + sha256 + "\nsize " + size + "\n";
    }

    public static class LfsPointer {
        public final String oid;
        public final long size;

        public LfsPointer(String oid, long size) {
            this.oid = oid;
            this.size = size;
        }
    }

    public static LfsPointer parsePointer(String content) {
        if (content == null || !content.startsWith("version draftflow-lfs/v1")) {
            return null;
        }
        String oid = null;
        long size = -1;
        for (String line : content.split("\\r?\\n")) {
            if (line.startsWith("oid sha256:")) {
                oid = line.substring(11).trim();
            } else if (line.startsWith("size ")) {
                try {
                    size = Long.parseLong(line.substring(5).trim());
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        }
        if (oid != null && size >= 0) {
            return new LfsPointer(oid, size);
        }
        return null;
    }

    public static void restoreLfsFile(Path repoRoot, LfsPointer ptr, Path destFile) throws IOException {
        Path lfsCache = repoRoot.resolve(".draftflow").resolve("lfs");
        Path targetFile = lfsCache.resolve(ptr.oid.substring(0, 2)).resolve(ptr.oid.substring(2));
        if (Files.exists(targetFile)) {
            Files.createDirectories(destFile.getParent());
            Files.copy(targetFile, destFile, StandardCopyOption.REPLACE_EXISTING);
        } else {
            throw new IOException("LFS object not found in cache: " + ptr.oid);
        }
    }

    public static void pushLfsObject(Path repoRoot, String oid, Path remoteDir) throws IOException {
        Path src = repoRoot.resolve(".draftflow").resolve("lfs").resolve(oid.substring(0, 2)).resolve(oid.substring(2));
        if (!Files.exists(src)) {
            return;
        }
        Path dest = remoteDir.resolve("lfs").resolve(oid.substring(0, 2)).resolve(oid.substring(2));
        Files.createDirectories(dest.getParent());
        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
    }

    public static void pullLfsObject(Path repoRoot, String oid, Path remoteDir) throws IOException {
        Path src = remoteDir.resolve("lfs").resolve(oid.substring(0, 2)).resolve(oid.substring(2));
        if (!Files.exists(src)) {
            return;
        }
        Path dest = repoRoot.resolve(".draftflow").resolve("lfs").resolve(oid.substring(0, 2)).resolve(oid.substring(2));
        Files.createDirectories(dest.getParent());
        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
    }
}
