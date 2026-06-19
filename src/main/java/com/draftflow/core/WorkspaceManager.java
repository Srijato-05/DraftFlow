package com.draftflow.core;

import com.draftflow.cdc.FastCDC;
import com.draftflow.db.FileMetadata;
import com.draftflow.db.MetadataStore;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.*;

public class WorkspaceManager {
    private final CAS cas;
    private final MetadataStore db;

    public WorkspaceManager(CAS cas, MetadataStore db) {
        this.cas = cas;
        this.db = db;
    }

    /**
     * Scans the working directory for changes on the given set of files
     * and generates a new background shadow commit (draft revision).
     */
    public synchronized String scanAndCreateShadowCommit(Set<Path> changedPaths) throws IOException {
        Path rootDir = cas.getRootDir();
        DraftFlowConfig config = cas.getConfig();
        GitIgnoreMatcher ignoreMatcher = new GitIgnoreMatcher(rootDir, config.getExclude());

        // 1. Process changed files and update the index database
        for (Path path : changedPaths) {
            if (ignoreMatcher.isIgnored(path)) {
                continue;
            }
            Path relativePath = rootDir.relativize(path);
            String relStr = relativePath.toString().replace('\\', '/');

            if (Files.exists(path)) {
                if (Files.isDirectory(path)) {
                    continue; // Skip directories (only track files)
                }

                // Check if file is modified relative to what's in index
                long size = Files.size(path);
                long lastMod = Files.getLastModifiedTime(path).toMillis();
                FileMetadata cached = db.getFile(relStr);

                if (cached != null && cached.getSize() == size && cached.getLastModified() == lastMod) {
                    continue; // Unmodified
                }

                // File has changed, index it
                byte[] data = Files.readAllBytes(path);
                String objectHash;
                String typeStr;

                if (size > 1024 * 1024) { // > 1MB: Content-Defined Chunking
                    List<FastCDC.Chunk> chunks = FastCDC.chunk(data);
                    List<String> chunkHashes = new ArrayList<>();
                    List<Integer> chunkSizes = new ArrayList<>();

                    for (FastCDC.Chunk chunk : chunks) {
                        byte[] chunkBytes = chunk.getBytes();
                        Blob chunkBlob = new Blob(chunkBytes);
                        String chunkHash = cas.writeObject(chunkBlob);
                        chunkHashes.add(chunkHash);
                        chunkSizes.add(chunkBytes.length);
                    }

                    ChunkTree chunkTree = new ChunkTree(chunkHashes, chunkSizes, size);
                    objectHash = cas.writeObject(chunkTree);
                    typeStr = ObjectType.CHUNK_TREE.name();
                } else { // <= 1MB: Standard Blob
                    Blob blob = new Blob(data);
                    objectHash = cas.writeObject(blob);
                    typeStr = ObjectType.BLOB.name();
                }

                // Get mode (executable on Unix, standard 100644 on Windows usually)
                int mode = Files.isExecutable(path) ? 100755 : 100644;

                FileMetadata newMeta = new FileMetadata(relStr, size, lastMod, objectHash, typeStr, mode);
                db.putFile(newMeta);
            } else {
                // File deleted
                db.removeFile(relStr);
            }
        }

        // 2. Rebuild tree structure from index
        List<FileMetadata> trackedFiles = db.getAllFiles();
        String rootTreeHash = rebuildTree(trackedFiles);

        // 3. Get active branch parent
        String activeHead = db.getConfig("activeHead"); // e.g. "heads/main"
        String parentHash = null;
        if (activeHead != null) {
            parentHash = db.getRef(activeHead);
        }

        List<String> parents = new ArrayList<>();
        if (parentHash != null) {
            parents.add(parentHash);
        }

        String activeChangeId = db.getConfig("activeChangeId");
        if (activeChangeId == null) {
            activeChangeId = UUID.randomUUID().toString();
            db.setConfig("activeChangeId", activeChangeId);
        }

        // 4. Write draft revision
        Revision draftRev = new Revision(
                rootTreeHash,
                parents,
                activeChangeId,
                System.getProperty("user.name"),
                System.currentTimeMillis(),
                "shadow-revision (WIP)",
                true // Is draft
        );

        String draftRevHash = cas.writeObject(draftRev);

        // 5. Update active head pointer to this draft
        if (activeHead != null) {
            db.setRef(activeHead, draftRevHash);
        }
        db.setConfig("activeRevisionHash", draftRevHash);
        db.commit();

        return draftRevHash;
    }

    /**
     * Checks out the target revision, cleaning untracked files and materializing objects to disk.
     */
    public synchronized void restoreWorkingCopy(String targetRevisionHash) throws IOException {
        restoreWorkingCopyInternal(targetRevisionHash, true);
    }

    private synchronized void restoreWorkingCopyInternal(String targetRevisionHash, boolean allowRollback) throws IOException {
        String previousRevisionHash = db.getConfig("activeRevisionHash");
        List<FileMetadata> previousFiles = db.getAllFiles();

        Revision rev;
        try {
            rev = (Revision) cas.readObject(targetRevisionHash);
        } catch (IOException e) {
            throw new IOException("Failed to read revision " + targetRevisionHash + " from CAS", e);
        }

        String rootTreeHash = rev.getTreeHash();
        Path rootDir = cas.getRootDir();

        // 1. Clean current tracked files from working copy
        for (FileMetadata file : previousFiles) {
            Path p = rootDir.resolve(file.getPath());
            try {
                Files.deleteIfExists(p);
            } catch (IOException e) {
                // Ignore delete errors to keep restoration moving forward
            }
        }
        db.clearIndex();

        try {
            // 2. Materialize tree recursively
            materializeTree("", rootTreeHash);

            // 3. Update active config
            db.setConfig("activeRevisionHash", targetRevisionHash);
            db.setConfig("activeChangeId", rev.getChangeId());
            
            String activeHead = db.getConfig("activeHead");
            if (activeHead != null) {
                db.setRef(activeHead, targetRevisionHash);
            }

            db.commit();
        } catch (Exception e) {
            if (allowRollback && previousRevisionHash != null && !previousRevisionHash.equals(targetRevisionHash)) {
                try {
                    restoreWorkingCopyInternal(previousRevisionHash, false);
                } catch (Exception rollbackException) {
                    db.clearIndex();
                    db.commit();
                    throw new IOException("Critical: Restore failed for " + targetRevisionHash + 
                            " and rollback failed for " + previousRevisionHash + ". Workspace may be in an inconsistent state.", e);
                }
                throw new IOException("Restore failed for " + targetRevisionHash + ". Successfully rolled back to previous revision " + previousRevisionHash, e);
            } else {
                db.clearIndex();
                db.commit();
                throw new IOException("Restore failed for " + targetRevisionHash + ". No rollback possible.", e);
            }
        }
    }

    private void materializeTree(String currentPath, String treeHash) throws IOException {
        Tree tree = (Tree) cas.readObject(treeHash);
        Path rootDir = cas.getRootDir();

        for (TreeEntry entry : tree.getEntries()) {
            String relPath = currentPath.isEmpty() ? entry.getName() : currentPath + "/" + entry.getName();
            Path path = rootDir.resolve(relPath);

            if (entry.getType() == ObjectType.TREE) {
                Files.createDirectories(path);
                materializeTree(relPath, entry.getHash());
            } else if (entry.getType() == ObjectType.BLOB) {
                Files.createDirectories(path.getParent());
                Blob blob = (Blob) cas.readObject(entry.getHash());
                Files.write(path, blob.getContent());
                
                // Track in index
                long size = Files.size(path);
                long lastMod = Files.getLastModifiedTime(path).toMillis();
                FileMetadata meta = new FileMetadata(relPath, size, lastMod, entry.getHash(), ObjectType.BLOB.name(), entry.getMode());
                db.putFile(meta);
            } else if (entry.getType() == ObjectType.CHUNK_TREE) {
                Files.createDirectories(path.getParent());
                ChunkTree chunkTree = (ChunkTree) cas.readObject(entry.getHash());
                
                try (OutputStream os = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    for (String chunkHash : chunkTree.getChunkHashes()) {
                        Blob chunk = (Blob) cas.readObject(chunkHash);
                        os.write(chunk.getContent());
                    }
                }

                // Track in index
                long size = Files.size(path);
                long lastMod = Files.getLastModifiedTime(path).toMillis();
                FileMetadata meta = new FileMetadata(relPath, size, lastMod, entry.getHash(), ObjectType.CHUNK_TREE.name(), entry.getMode());
                db.putFile(meta);
            } else if (entry.getType() == ObjectType.CONFLICT) {
                Files.createDirectories(path.getParent());
                ConflictNode conflict = (ConflictNode) cas.readObject(entry.getHash());

                String leftContent = "";
                if (conflict.getLeftHash() != null) {
                    try {
                        Blob leftBlob = (Blob) cas.readObject(conflict.getLeftHash());
                        leftContent = new String(leftBlob.getContent());
                    } catch (Exception e) {
                        leftContent = "[Error reading Left Object]";
                    }
                } else {
                    leftContent = "(Deleted)";
                }

                String rightContent = "";
                if (conflict.getRightHash() != null) {
                    try {
                        Blob rightBlob = (Blob) cas.readObject(conflict.getRightHash());
                        rightContent = new String(rightBlob.getContent());
                    } catch (Exception e) {
                        rightContent = "[Error reading Right Object]";
                    }
                } else {
                    rightContent = "(Deleted)";
                }

                String markerText = "<<<<<<< OURS\n" + leftContent + "\n=======\n" + rightContent + "\n>>>>>>> THEIRS\n";
                Files.writeString(path, markerText);

                // Track in index
                long size = Files.size(path);
                long lastMod = Files.getLastModifiedTime(path).toMillis();
                FileMetadata meta = new FileMetadata(relPath, size, lastMod, entry.getHash(), ObjectType.CONFLICT.name(), entry.getMode());
                db.putFile(meta);
            }
        }
    }

    private String rebuildTree(List<FileMetadata> files) throws IOException {
        TreeNode rootNode = new TreeNode("", ObjectType.TREE, 040000);

        for (FileMetadata file : files) {
            String[] segments = file.getPath().split("/");
            TreeNode current = rootNode;

            for (int i = 0; i < segments.length; i++) {
                String segment = segments[i];
                boolean isLast = (i == segments.length - 1);

                if (isLast) {
                    ObjectType type = ObjectType.valueOf(file.getType());
                    current.children.put(segment, new TreeNode(segment, type, file.getMode(), file.getHash()));
                } else {
                    current.children.putIfAbsent(segment, new TreeNode(segment, ObjectType.TREE, 040000));
                    current = current.children.get(segment);
                }
            }
        }

        return writeTreeNode(rootNode);
    }

    private String writeTreeNode(TreeNode node) throws IOException {
        if (node.type != ObjectType.TREE) {
            return node.hash;
        }

        List<TreeEntry> entries = new ArrayList<>();
        for (TreeNode child : node.children.values()) {
            String childHash = writeTreeNode(child);
            entries.add(new TreeEntry(child.name, childHash, child.type, child.mode));
        }

        Tree tree = new Tree(entries);
        return cas.writeObject(tree);
    }

    private static class TreeNode {
        final String name;
        final ObjectType type;
        final int mode;
        String hash;
        final Map<String, TreeNode> children = new HashMap<>();

        // Directory node constructor
        TreeNode(String name, ObjectType type, int mode) {
            this.name = name;
            this.type = type;
            this.mode = mode;
        }

        // File node constructor
        TreeNode(String name, ObjectType type, int mode, String hash) {
            this.name = name;
            this.type = type;
            this.mode = mode;
            this.hash = hash;
        }
    }
}
