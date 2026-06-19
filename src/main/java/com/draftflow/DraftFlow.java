package com.draftflow;

import com.draftflow.core.*;
import com.draftflow.cdc.FastCDC;
import com.draftflow.db.FileMetadata;
import com.draftflow.db.MetadataStore;
import com.draftflow.merge.MergeEngine;
import com.draftflow.remote.*;
import com.draftflow.ui.UiServer;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.Callable;

@Command(name = "draftflow", mixinStandardHelpOptions = true, version = "draftflow 1.0",
        description = "DraftFlow: High-Performance Snapshot-based DAG Version Control System",
        subcommands = {
                DraftFlow.SetupCmd.class,
                DraftFlow.StatusCmd.class,
                DraftFlow.SaveCmd.class,
                DraftFlow.SwitchCmd.class,
                DraftFlow.MergeCmd.class,
                DraftFlow.ResolveCmd.class,
                DraftFlow.UploadCmd.class,
                DraftFlow.DownloadCmd.class,
                DraftFlow.HistoryCmd.class,
                DraftFlow.BranchCmd.class,
                DraftFlow.UndoCmd.class,
                DraftFlow.DashboardCmd.class
        })
public class DraftFlow implements Callable<Integer> {

    public static Path getCurrentDir() {
        return Paths.get(System.getProperty("draftflow.dir", ".")).toAbsolutePath().normalize();
    }

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new DraftFlow()).execute(args);
        System.exit(exitCode);
    }

    // --- SUBCOMMANDS ---

    @Command(name = "setup", description = "Initialize a new, empty DraftFlow repository")
    public static class SetupCmd implements Callable<Integer> {
        @Override
        public Integer call() throws Exception {
            Path currentDir = getCurrentDir();
            CAS cas = new CAS(currentDir);
            if (Files.exists(cas.getDraftFlowDir())) {
                System.out.println("DraftFlow repository already initialized in " + cas.getRootDir());
                return 0;
            }
            cas.init();
            Path dbPath = cas.getDraftFlowDir().resolve("index").resolve("index.mv.db");
            try (MetadataStore db = new MetadataStore(dbPath)) {
                db.open();
                db.setConfig("activeHead", "heads/main");
                db.commit();
            }
            System.out.println("Initialized empty DraftFlow repository in " + cas.getDraftFlowDir());
            return 0;
        }
    }

    @Command(name = "status", description = "Show the working tree status")
    public static class StatusCmd implements Callable<Integer> {
        @Override
        public Integer call() throws Exception {
            Path currentDir = getCurrentDir();
            CAS cas = new CAS(currentDir);
            if (!Files.exists(cas.getDraftFlowDir())) {
                System.err.println("Fatal: Not a draftflow repository.");
                return 1;
            }
            Path dbPath = cas.getDraftFlowDir().resolve("index").resolve("index.mv.db");
            try (MetadataStore db = new MetadataStore(dbPath)) {
                db.open();
                String activeHead = db.getConfig("activeHead");
                String activeRev = db.getConfig("activeRevisionHash");
                String activeChange = db.getConfig("activeChangeId");

                System.out.println("On branch: " + (activeHead != null ? activeHead.replace("heads/", "") : "detached"));
                System.out.println("Active Change ID: " + activeChange);
                System.out.println("Active Revision: " + (activeRev != null ? activeRev : "None"));
                System.out.println();

                List<FileMetadata> tracked = db.getAllFiles();
                List<String> modified = new ArrayList<>();
                List<String> deleted = new ArrayList<>();
                List<String> conflicts = new ArrayList<>();

                for (FileMetadata file : tracked) {
                    Path p = cas.getRootDir().resolve(file.getPath());
                    if (!Files.exists(p)) {
                        deleted.add(file.getPath());
                    } else {
                        if (file.getType().equals(ObjectType.CONFLICT.name())) {
                            conflicts.add(file.getPath());
                        } else {
                            long size = Files.size(p);
                            long lastMod = Files.getLastModifiedTime(p).toMillis();
                            if (size != file.getSize() || lastMod != file.getLastModified()) {
                                modified.add(file.getPath());
                            }
                        }
                    }
                }

                if (conflicts.isEmpty() && modified.isEmpty() && deleted.isEmpty()) {
                    System.out.println("Working copy is clean.");
                } else {
                    if (!conflicts.isEmpty()) {
                        System.out.println("Unresolved conflicts:");
                        for (String f : conflicts) {
                            System.out.println("  (conflict)  " + f);
                        }
                        System.out.println("  (use \"draftflow resolve\" to resolve conflicts)");
                        System.out.println();
                    }
                    if (!modified.isEmpty()) {
                        System.out.println("Modified files:");
                        for (String f : modified) {
                            System.out.println("  (modified)  " + f);
                        }
                        System.out.println();
                    }
                    if (!deleted.isEmpty()) {
                        System.out.println("Deleted files:");
                        for (String f : deleted) {
                            System.out.println("  (deleted)   " + f);
                        }
                        System.out.println();
                    }
                }
            }
            return 0;
        }
    }

    @Command(name = "save", description = "Promote current working tree changes to a permanent commit")
    public static class SaveCmd implements Callable<Integer> {
        @Option(names = {"-m", "--message"}, description = "Commit message", required = true)
        private String message;

        @Override
        public Integer call() throws Exception {
            Path currentDir = getCurrentDir();
            CAS cas = new CAS(currentDir);
            if (!Files.exists(cas.getDraftFlowDir())) {
                System.err.println("Fatal: Not a draftflow repository.");
                return 1;
            }
            Path dbPath = cas.getDraftFlowDir().resolve("index").resolve("index.mv.db");
            try (MetadataStore db = new MetadataStore(dbPath)) {
                db.open();

                List<FileMetadata> allTracked = db.getAllFiles();
                for (FileMetadata f : allTracked) {
                    if (f.getType().equals(ObjectType.CONFLICT.name())) {
                        System.err.println("Fatal: Cannot save with unresolved conflicts (" + f.getPath() + "). Run 'draftflow resolve' first.");
                        return 1;
                    }
                }

                WorkspaceManager wm = new WorkspaceManager(cas, db);
                Set<Path> allFiles = new HashSet<>();
                Files.walkFileTree(cas.getRootDir(), new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        if (dir.getFileName() != null && dir.getFileName().toString().equals(".draftflow")) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        allFiles.add(file);
                        return FileVisitResult.CONTINUE;
                    }
                });

                for (FileMetadata meta : db.getAllFiles()) {
                    allFiles.add(cas.getRootDir().resolve(meta.getPath()));
                }

                String latestRevHash = wm.scanAndCreateShadowCommit(allFiles);
                Revision draft = (Revision) cas.readObject(latestRevHash);

                Revision permanent = new Revision(
                        draft.getTreeHash(),
                        draft.getParentHashes(),
                        draft.getChangeId(),
                        System.getProperty("user.name"),
                        System.currentTimeMillis(),
                        message,
                        false
                );

                String permanentHash = cas.writeObject(permanent);
                String activeHead = db.getConfig("activeHead");
                if (activeHead != null) {
                    db.setRef(activeHead, permanentHash);
                }
                db.setConfig("activeRevisionHash", permanentHash);
                db.setChangeRevision(draft.getChangeId(), permanentHash);

                Revision newDraft = new Revision(
                        draft.getTreeHash(),
                        Collections.singletonList(permanentHash),
                        draft.getChangeId(),
                        System.getProperty("user.name"),
                        System.currentTimeMillis(),
                        "shadow-revision (WIP)",
                        true
                );
                String newDraftHash = cas.writeObject(newDraft);
                if (activeHead != null) {
                    db.setRef(activeHead, newDraftHash);
                }
                db.setConfig("activeRevisionHash", newDraftHash);
                db.commit();

                System.out.println("Saved change " + draft.getChangeId().substring(0, 8) + " as revision: " + permanentHash.substring(0, 8));
            }
            return 0;
        }
    }

    @Command(name = "switch", description = "Switch to a specific revision hash")
    public static class SwitchCmd implements Callable<Integer> {
        @Parameters(index = "0", description = "Revision hash to check out")
        private String revisionHash;

        @Override
        public Integer call() throws Exception {
            Path currentDir = getCurrentDir();
            CAS cas = new CAS(currentDir);
            if (!Files.exists(cas.getDraftFlowDir())) {
                System.err.println("Fatal: Not a draftflow repository.");
                return 1;
            }
            Path dbPath = cas.getDraftFlowDir().resolve("index").resolve("index.mv.db");
            try (MetadataStore db = new MetadataStore(dbPath)) {
                db.open();
                WorkspaceManager wm = new WorkspaceManager(cas, db);

                String fullHash = cas.resolveHash(revisionHash);
                if (fullHash == null) {
                    System.err.println("Error: Revision not found: " + revisionHash);
                    return 1;
                }

                wm.restoreWorkingCopy(fullHash);
                System.out.println("Switched to revision: " + fullHash.substring(0, 8));
            }
            return 0;
        }
    }

    @Command(name = "merge", description = "Merge another revision into the current branch")
    public static class MergeCmd implements Callable<Integer> {
        @Parameters(index = "0", description = "Revision hash or branch ref to merge")
        private String target;

        @Override
        public Integer call() throws Exception {
            Path currentDir = getCurrentDir();
            CAS cas = new CAS(currentDir);
            if (!Files.exists(cas.getDraftFlowDir())) {
                System.err.println("Fatal: Not a draftflow repository.");
                return 1;
            }
            Path dbPath = cas.getDraftFlowDir().resolve("index").resolve("index.mv.db");
            try (MetadataStore db = new MetadataStore(dbPath)) {
                db.open();

                String oursHash = db.getConfig("activeRevisionHash");
                if (oursHash == null) {
                    System.err.println("Error: No active revision on current branch.");
                    return 1;
                }

                String theirsHash = cas.resolveHash(target);
                if (theirsHash == null) {
                    theirsHash = db.getRef("heads/" + target);
                    if (theirsHash == null) {
                        theirsHash = db.getRef(target);
                    }
                }

                if (theirsHash == null) {
                    System.err.println("Error: Could not resolve target: " + target);
                    return 1;
                }

                System.out.println("Merging " + theirsHash.substring(0, 8) + " into current revision " + oursHash.substring(0, 8) + "...");
                
                MergeEngine.MergeResult result = MergeEngine.mergeRevisions(oursHash, theirsHash, cas);

                List<String> parents = Arrays.asList(oursHash, theirsHash);
                String activeChangeId = db.getConfig("activeChangeId");

                Revision mergedDraft = new Revision(
                        result.treeHash,
                        parents,
                        activeChangeId,
                        System.getProperty("user.name"),
                        System.currentTimeMillis(),
                        "Merge commit draft",
                        true
                );
                String mergedDraftHash = cas.writeObject(mergedDraft);

                WorkspaceManager wm = new WorkspaceManager(cas, db);
                wm.restoreWorkingCopy(mergedDraftHash);

                if (result.clean) {
                    System.out.println("Merge successful! Clean merged state checked out.");
                } else {
                    System.out.println("Merge finished with CONFLICTS!");
                    System.out.println("Conflicted files:");
                    for (String f : result.conflicts) {
                        System.out.println("  (conflict)  " + f);
                    }
                    System.out.println("Run 'draftflow resolve' to resolve conflicts interactively.");
                }
            }
            return 0;
        }
    }

    @Command(name = "resolve", description = "Resolve conflicts in the working copy interactively")
    public static class ResolveCmd implements Callable<Integer> {
        @SuppressWarnings("resource")
        @Override
        public Integer call() throws Exception {
            Path currentDir = getCurrentDir();
            CAS cas = new CAS(currentDir);
            if (!Files.exists(cas.getDraftFlowDir())) {
                System.err.println("Fatal: Not a draftflow repository.");
                return 1;
            }
            Path dbPath = cas.getDraftFlowDir().resolve("index").resolve("index.mv.db");
            try (MetadataStore db = new MetadataStore(dbPath)) {
                db.open();

                List<FileMetadata> allFiles = db.getAllFiles();
                List<FileMetadata> conflicts = new ArrayList<>();
                for (FileMetadata f : allFiles) {
                    if (f.getType().equals(ObjectType.CONFLICT.name())) {
                        conflicts.add(f);
                    }
                }

                if (conflicts.isEmpty()) {
                    System.out.println("No unresolved conflicts.");
                    return 0;
                }

                Scanner scanner = new Scanner(System.in);
                WorkspaceManager wm = new WorkspaceManager(cas, db);

                System.out.println("Found " + conflicts.size() + " unresolved conflict(s).");
                for (FileMetadata f : conflicts) {
                    System.out.println("\n----------------------------------------");
                    System.out.println("Conflicting file: " + f.getPath());

                    ConflictNode node = (ConflictNode) cas.readObject(f.getHash());

                    System.out.println("Select resolution action:");
                    System.out.println("  1. Keep OURS (Left) version");
                    System.out.println("  2. Keep THEIRS (Right) version");
                    System.out.println("  3. Resolve manually (Verify file no longer has markers on disk)");
                    System.out.print("Enter choice [1-3]: ");

                    String choice = scanner.nextLine().trim();
                    if (choice.equals("1")) {
                        if (node.getLeftHash() == null) {
                            db.removeFile(f.getPath());
                            Files.deleteIfExists(cas.getRootDir().resolve(f.getPath()));
                            System.out.println("Resolved " + f.getPath() + " by deleting it (Ours).");
                        } else {
                            Blob blob = (Blob) cas.readObject(node.getLeftHash());
                            Path path = cas.getRootDir().resolve(f.getPath());
                            Files.write(path, blob.getContent());
                            long size = Files.size(path);
                            long lastMod = Files.getLastModifiedTime(path).toMillis();
                            FileMetadata resolved = new FileMetadata(f.getPath(), size, lastMod, node.getLeftHash(), ObjectType.BLOB.name(), f.getMode());
                            db.putFile(resolved);
                            System.out.println("Resolved " + f.getPath() + " using OURS version.");
                        }
                    } else if (choice.equals("2")) {
                        if (node.getRightHash() == null) {
                            db.removeFile(f.getPath());
                            Files.deleteIfExists(cas.getRootDir().resolve(f.getPath()));
                            System.out.println("Resolved " + f.getPath() + " by deleting it (Theirs).");
                        } else {
                            Blob blob = (Blob) cas.readObject(node.getRightHash());
                            Path path = cas.getRootDir().resolve(f.getPath());
                            Files.write(path, blob.getContent());
                            long size = Files.size(path);
                            long lastMod = Files.getLastModifiedTime(path).toMillis();
                            FileMetadata resolved = new FileMetadata(f.getPath(), size, lastMod, node.getRightHash(), ObjectType.BLOB.name(), f.getMode());
                            db.putFile(resolved);
                            System.out.println("Resolved " + f.getPath() + " using THEIRS version.");
                        }
                    } else if (choice.equals("3")) {
                        Path path = cas.getRootDir().resolve(f.getPath());
                        if (!Files.exists(path)) {
                            System.err.println("File does not exist on disk. If deleted, select option 1 or 2.");
                            continue;
                        }
                        String content = Files.readString(path);
                        if (content.contains("<<<<<<< OURS") || content.contains("=======") || content.contains(">>>>>>> THEIRS")) {
                            System.err.println("Warning: File still contains conflict markers on disk! Clean the file first, then try resolving manually again.");
                        } else {
                            byte[] data = content.getBytes(StandardCharsets.UTF_8);
                            String newBlobHash;
                            String typeStr;

                            if (data.length > 1024 * 1024) {
                                List<FastCDC.Chunk> chunks = FastCDC.chunk(data);
                                List<String> chunkHashes = new ArrayList<>();
                                List<Integer> chunkSizes = new ArrayList<>();
                                for (FastCDC.Chunk chunk : chunks) {
                                    byte[] cb = chunk.getBytes();
                                    Blob cblob = new Blob(cb);
                                    chunkHashes.add(cas.writeObject(cblob));
                                    chunkSizes.add(cb.length);
                                }
                                ChunkTree ct = new ChunkTree(chunkHashes, chunkSizes, data.length);
                                newBlobHash = cas.writeObject(ct);
                                typeStr = ObjectType.CHUNK_TREE.name();
                            } else {
                                Blob blob = new Blob(data);
                                newBlobHash = cas.writeObject(blob);
                                typeStr = ObjectType.BLOB.name();
                            }

                            long size = Files.size(path);
                            long lastMod = Files.getLastModifiedTime(path).toMillis();
                            FileMetadata resolved = new FileMetadata(f.getPath(), size, lastMod, newBlobHash, typeStr, f.getMode());
                            db.putFile(resolved);
                            System.out.println("Staged manually resolved file: " + f.getPath());
                        }
                    } else {
                        System.out.println("Invalid choice. Skipping.");
                    }
                }

                Set<Path> scanned = new HashSet<>();
                for (FileMetadata f : db.getAllFiles()) {
                    scanned.add(cas.getRootDir().resolve(f.getPath()));
                }
                wm.scanAndCreateShadowCommit(scanned);
                db.commit();
                System.out.println("\nResolve run completed.");
            }
            return 0;
        }
    }

    @Command(name = "upload", description = "Upload branch commits to a remote repository")
    public static class UploadCmd implements Callable<Integer> {
        @Option(names = {"--remote"}, description = "Remote repository URL (e.g. file:///... or http://...)", required = true)
        private String remoteUrl;

        @Override
        public Integer call() throws Exception {
            Path currentDir = getCurrentDir();
            CAS cas = new CAS(currentDir);
            if (!Files.exists(cas.getDraftFlowDir())) {
                System.err.println("Fatal: Not a draftflow repository.");
                return 1;
            }
            Path dbPath = cas.getDraftFlowDir().resolve("index").resolve("index.mv.db");
            try (MetadataStore db = new MetadataStore(dbPath)) {
                db.open();
                String activeHead = db.getConfig("activeHead");
                if (activeHead == null) {
                    System.err.println("Error: No branch active to upload.");
                    return 1;
                }
                String localHead = db.getConfig("activeRevisionHash");
                if (localHead == null) {
                    System.err.println("Error: Branch is empty.");
                    return 1;
                }

                RemoteClient client = new RemoteClient(remoteUrl);
                String remoteHead = client.getRef(activeHead);

                List<String> missingHashes = new ArrayList<>();
                Queue<String> queue = new LinkedList<>();
                queue.add(localHead);
                Set<String> visited = new HashSet<>();

                while (!queue.isEmpty()) {
                    String curr = queue.poll();
                    if (curr == null || visited.contains(curr) || curr.equals(remoteHead)) {
                        continue;
                    }
                    visited.add(curr);
                    missingHashes.add(curr);

                    collectReferencedObjects(curr, cas, missingHashes);

                    Revision rev = (Revision) cas.readObject(curr);
                    queue.addAll(rev.getParentHashes());
                }

                if (missingHashes.isEmpty()) {
                    System.out.println("Everything up-to-date.");
                    return 0;
                }

                System.out.println("Packing " + missingHashes.size() + " objects...");
                String packId = "pack-" + UUID.randomUUID().toString().substring(0, 8);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                Packer.createPack(missingHashes, cas, out);
                byte[] packData = out.toByteArray();

                System.out.println("Uploading pack " + packId + " to remote...");
                client.uploadPack(packId, packData);

                Map<String, String> remoteIndex = client.downloadIndex();
                for (String h : missingHashes) {
                    remoteIndex.put(h, packId);
                }
                client.uploadIndex(remoteIndex);

                System.out.println("Updating remote ref " + activeHead + "...");
                try {
                    OCC.tryUpdateRef(client, activeHead, remoteHead, localHead);
                    System.out.println("Upload successful!");
                } catch (OCC.ConcurrencyException e) {
                    System.err.println("Upload failed: " + e.getMessage());
                    return 1;
                }
            }
            return 0;
        }

        private void collectReferencedObjects(String revHash, CAS cas, List<String> list) {
            try {
                Revision rev = (Revision) cas.readObject(revHash);
                String treeHash = rev.getTreeHash();
                if (treeHash != null && !list.contains(treeHash)) {
                    list.add(treeHash);
                    collectTreeObjects(treeHash, cas, list);
                }
            } catch (Exception ignored) {}
        }

        private void collectTreeObjects(String treeHash, CAS cas, List<String> list) {
            try {
                Tree tree = (Tree) cas.readObject(treeHash);
                for (TreeEntry entry : tree.getEntries()) {
                    String hash = entry.getHash();
                    if (hash != null && !list.contains(hash)) {
                        list.add(hash);
                        if (entry.getType() == ObjectType.TREE) {
                            collectTreeObjects(hash, cas, list);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    @Command(name = "download", description = "Download branch commits from a remote repository")
    public static class DownloadCmd implements Callable<Integer> {
        @Option(names = {"--remote"}, description = "Remote repository URL", required = true)
        private String remoteUrl;

        @Override
        public Integer call() throws Exception {
            Path currentDir = getCurrentDir();
            CAS cas = new CAS(currentDir);
            if (!Files.exists(cas.getDraftFlowDir())) {
                System.err.println("Fatal: Not a draftflow repository.");
                return 1;
            }
            Path dbPath = cas.getDraftFlowDir().resolve("index").resolve("index.mv.db");
            try (MetadataStore db = new MetadataStore(dbPath)) {
                db.open();
                String activeHead = db.getConfig("activeHead");
                if (activeHead == null) {
                    System.err.println("Error: No active branch to download into.");
                    return 1;
                }

                RemoteClient client = new RemoteClient(remoteUrl);
                String remoteHead = client.getRef(activeHead);
                if (remoteHead == null) {
                    System.out.println("Branch " + activeHead + " does not exist on remote.");
                    return 0;
                }

                String localHead = db.getConfig("activeRevisionHash");
                if (remoteHead.equals(localHead)) {
                    System.out.println("Already up-to-date.");
                    return 0;
                }

                Map<String, String> remoteIndex = client.downloadIndex();
                Set<String> missing = new HashSet<>();
                Queue<String> queue = new LinkedList<>();
                queue.add(remoteHead);

                while (!queue.isEmpty()) {
                    String curr = queue.poll();
                    if (curr == null || missing.contains(curr)) {
                        continue;
                    }
                    Path objPath = cas.getDraftFlowDir().resolve("objects").resolve(curr.substring(0, 2)).resolve(curr.substring(2));
                    if (Files.exists(objPath)) {
                        continue;
                    }

                    missing.add(curr);
                    String packId = remoteIndex.get(curr);
                    if (packId != null) {
                        System.out.println("Downloading missing pack: " + packId);
                        byte[] packData = client.downloadPack(packId);
                        ByteArrayInputStream in = new ByteArrayInputStream(packData);
                        Packer.unpack(in, cas);
                    }

                    try {
                        Revision rev = (Revision) cas.readObject(curr);
                        queue.addAll(rev.getParentHashes());
                    } catch (Exception ignored) {}
                }

                db.setRef(activeHead, remoteHead);
                db.setConfig("activeRevisionHash", remoteHead);
                db.commit();

                WorkspaceManager wm = new WorkspaceManager(cas, db);
                wm.restoreWorkingCopy(remoteHead);

                System.out.println("Download successful! Updated local head to: " + remoteHead.substring(0, 8));
            }
            return 0;
        }
    }

    @Command(name = "history", description = "Show DAG commit history")
    public static class HistoryCmd implements Callable<Integer> {
        @Override
        public Integer call() throws Exception {
            Path currentDir = getCurrentDir();
            CAS cas = new CAS(currentDir);
            if (!Files.exists(cas.getDraftFlowDir())) {
                System.err.println("Fatal: Not a draftflow repository.");
                return 1;
            }
            Path dbPath = cas.getDraftFlowDir().resolve("index").resolve("index.mv.db");
            try (MetadataStore db = new MetadataStore(dbPath)) {
                db.open();
                String activeRev = db.getConfig("activeRevisionHash");
                if (activeRev == null) {
                    System.out.println("No commits in this branch.");
                    return 0;
                }

                Queue<String> queue = new LinkedList<>();
                queue.add(activeRev);
                Set<String> visited = new HashSet<>();

                while (!queue.isEmpty()) {
                    String curr = queue.poll();
                    if (curr == null || visited.contains(curr)) {
                        continue;
                    }
                    visited.add(curr);

                    Revision rev = (Revision) cas.readObject(curr);
                    System.out.println("*  Revision: " + curr.substring(0, 8) + (rev.isDraft() ? " (DRAFT)" : ""));
                    System.out.println("|  Change ID: " + rev.getChangeId().substring(0, 8));
                    System.out.println("|  Author: " + rev.getAuthor());
                    System.out.println("|  Date: " + new java.util.Date(rev.getTimestamp()));
                    System.out.println("|  Message: " + rev.getMessage());
                    System.out.println("|");

                    queue.addAll(rev.getParentHashes());
                }
            }
            return 0;
        }
    }

    @Command(name = "branch", description = "List, create, or delete branches")
    public static class BranchCmd implements Callable<Integer> {
        @Option(names = {"-c", "--create"}, description = "Create a branch with the specified name")
        private String newBranch;

        @Option(names = {"-d", "--delete"}, description = "Delete the branch with the specified name")
        private String deleteBranch;

        @Override
        public Integer call() throws Exception {
            Path currentDir = getCurrentDir();
            CAS cas = new CAS(currentDir);
            if (!Files.exists(cas.getDraftFlowDir())) {
                System.err.println("Fatal: Not a draftflow repository.");
                return 1;
            }
            Path dbPath = cas.getDraftFlowDir().resolve("index").resolve("index.mv.db");
            try (MetadataStore db = new MetadataStore(dbPath)) {
                db.open();
                if (newBranch != null) {
                    String head = db.getConfig("activeRevisionHash");
                    db.setRef("heads/" + newBranch, head);
                    db.commit();
                    System.out.println("Created branch: " + newBranch);
                } else if (deleteBranch != null) {
                    db.removeRef("heads/" + deleteBranch);
                    db.commit();
                    System.out.println("Deleted branch: " + deleteBranch);
                } else {
                    String active = db.getConfig("activeHead");
                    for (String name : db.getRefNames()) {
                        if (name.startsWith("heads/")) {
                            String branch = name.replace("heads/", "");
                            if (name.equals(active)) {
                                System.out.println("* " + branch);
                            } else {
                                System.out.println("  " + branch);
                            }
                        }
                    }
                }
            }
            return 0;
        }
    }

    @Command(name = "undo", description = "Undo the last commit / revert HEAD pointer to parent")
    public static class UndoCmd implements Callable<Integer> {
        @Override
        public Integer call() throws Exception {
            Path currentDir = getCurrentDir();
            CAS cas = new CAS(currentDir);
            if (!Files.exists(cas.getDraftFlowDir())) {
                System.err.println("Fatal: Not a draftflow repository.");
                return 1;
            }
            Path dbPath = cas.getDraftFlowDir().resolve("index").resolve("index.mv.db");
            try (MetadataStore db = new MetadataStore(dbPath)) {
                db.open();
                String activeRev = db.getConfig("activeRevisionHash");
                if (activeRev == null) {
                    System.err.println("Error: Nothing to undo.");
                    return 1;
                }

                String curr = activeRev;
                Revision discardRev = null;
                while (curr != null) {
                    Revision r = (Revision) cas.readObject(curr);
                    if (!r.isDraft()) {
                        discardRev = r;
                        break;
                    }
                    if (r.getParentHashes().isEmpty()) {
                        break;
                    }
                    curr = r.getParentHashes().get(0);
                }

                if (discardRev == null) {
                    System.err.println("Error: No permanent commit to undo.");
                    return 1;
                }

                if (discardRev.getParentHashes().isEmpty()) {
                    System.err.println("Error: Root revision reached, cannot undo.");
                    return 1;
                }

                String parent = discardRev.getParentHashes().get(0);
                String activeHead = db.getConfig("activeHead");
                if (activeHead != null) {
                    db.setRef(activeHead, parent);
                }
                db.setConfig("activeRevisionHash", parent);
                db.commit();

                WorkspaceManager wm = new WorkspaceManager(cas, db);
                wm.restoreWorkingCopy(parent);

                System.out.println("Reverted branch ref back to: " + parent.substring(0, 8));
            }
            return 0;
        }
    }

    @Command(name = "dashboard", description = "Launch visualization dashboard server")
    public static class DashboardCmd implements Callable<Integer> {
        @Option(names = {"-p", "--port"}, description = "Port to listen on", defaultValue = "8080")
        private int port;

        @Override
        public Integer call() throws Exception {
            Path currentDir = getCurrentDir();
            CAS cas = new CAS(currentDir);
            if (!Files.exists(cas.getDraftFlowDir())) {
                System.err.println("Fatal: Not a draftflow repository.");
                return 1;
            }
            Path dbPath = cas.getDraftFlowDir().resolve("index").resolve("index.mv.db");
            
            MetadataStore db = new MetadataStore(dbPath);
            db.open();

            UiServer uiServer = new UiServer(cas, db, port);
            uiServer.start();

            try {
                if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                    java.awt.Desktop.getDesktop().browse(new URI("http://localhost:" + uiServer.getPort()));
                } else {
                    System.out.println("Dashboard: http://localhost:" + uiServer.getPort());
                }
            } catch (Exception e) {
                System.out.println("Dashboard: http://localhost:" + uiServer.getPort());
            }

            System.out.println("Press Ctrl+C to terminate UI server...");
            
            Thread.currentThread().join();
            
            db.close();
            return 0;
        }
    }
}
