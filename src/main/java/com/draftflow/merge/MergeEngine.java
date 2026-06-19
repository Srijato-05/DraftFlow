package com.draftflow.merge;

import com.draftflow.cdc.FastCDC;
import com.draftflow.core.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MergeEngine {

    public static class MergeResult {
        public final String treeHash;
        public final boolean clean;
        public final List<String> conflicts;

        public MergeResult(String treeHash, boolean clean, List<String> conflicts) {
            this.treeHash = treeHash;
            this.clean = clean;
            this.conflicts = conflicts;
        }
    }

    public static class TreeEntryInfo {
        public String path;
        public String hash;
        public ObjectType type;
        public int mode;
    }

    /**
     * Merges two revisions by finding their Lowest Common Ancestor (LCA)
     * and performing a 3-way tree-level merge.
     */
    public static MergeResult mergeRevisions(String oursRevHash, String theirsRevHash, CAS cas) throws IOException {
        String baseRevHash = AncestorFinder.findLCA(oursRevHash, theirsRevHash, cas);
        
        String baseTreeHash = null;
        if (baseRevHash != null) {
            Revision baseRev = (Revision) cas.readObject(baseRevHash);
            baseTreeHash = baseRev.getTreeHash();
        }

        Revision oursRev = (Revision) cas.readObject(oursRevHash);
        Revision theirsRev = (Revision) cas.readObject(theirsRevHash);

        return mergeTrees(baseTreeHash, oursRev.getTreeHash(), theirsRev.getTreeHash(), cas);
    }

    /**
     * Merges three tree snapshots using 3-way merge rules.
     */
    public static MergeResult mergeTrees(String baseTreeHash, String oursTreeHash, String theirsTreeHash, CAS cas) throws IOException {
        Map<String, TreeEntryInfo> baseFlat = flattenTree(baseTreeHash, cas);
        Map<String, TreeEntryInfo> oursFlat = flattenTree(oursTreeHash, cas);
        Map<String, TreeEntryInfo> theirsFlat = flattenTree(theirsTreeHash, cas);

        Set<String> allPaths = new HashSet<>();
        allPaths.addAll(baseFlat.keySet());
        allPaths.addAll(oursFlat.keySet());
        allPaths.addAll(theirsFlat.keySet());

        Map<String, TreeEntryInfo> mergedFlat = new HashMap<>();
        List<String> conflictedPaths = new ArrayList<>();
        boolean clean = true;

        for (String path : allPaths) {
            TreeEntryInfo baseEntry = baseFlat.get(path);
            TreeEntryInfo oursEntry = oursFlat.get(path);
            TreeEntryInfo theirsEntry = theirsFlat.get(path);

            // Case 1: Ours and Theirs are identical
            if (entriesEqual(oursEntry, theirsEntry)) {
                if (oursEntry != null) {
                    mergedFlat.put(path, oursEntry);
                }
                continue;
            }

            // Case 2: Only Theirs changed relative to Base
            if (entriesEqual(oursEntry, baseEntry)) {
                if (theirsEntry != null) {
                    mergedFlat.put(path, theirsEntry);
                }
                continue;
            }

            // Case 3: Only Ours changed relative to Base
            if (entriesEqual(theirsEntry, baseEntry)) {
                if (oursEntry != null) {
                    mergedFlat.put(path, oursEntry);
                }
                continue;
            }

            // Case 4: Both changed differently
            if (oursEntry == null && theirsEntry == null) {
                // Both deleted, cleanly remove
                continue;
            }

            // Conflict: one deleted, the other modified/added
            if (oursEntry == null || theirsEntry == null) {
                clean = false;
                conflictedPaths.add(path);
                
                ConflictNode conflict = new ConflictNode(
                        baseEntry != null ? baseEntry.hash : null,
                        oursEntry != null ? oursEntry.hash : null,
                        theirsEntry != null ? theirsEntry.hash : null,
                        path
                );
                String conflictHash = cas.writeObject(conflict);
                
                TreeEntryInfo info = new TreeEntryInfo();
                info.path = path;
                info.hash = conflictHash;
                info.type = ObjectType.CONFLICT;
                info.mode = oursEntry != null ? oursEntry.mode : (theirsEntry != null ? theirsEntry.mode : 0100644);
                mergedFlat.put(path, info);
                continue;
            }

            // Both modified. Check if they are files.
            if (isFile(oursEntry) && isFile(theirsEntry)) {
                byte[] baseBytes = baseEntry != null ? readFileBytes(baseEntry, cas) : new byte[0];
                byte[] oursBytes = readFileBytes(oursEntry, cas);
                byte[] theirsBytes = readFileBytes(theirsEntry, cas);

                List<String> baseLines = toLines(baseBytes);
                List<String> oursLines = toLines(oursBytes);
                List<String> theirsLines = toLines(theirsBytes);

                LineMerge.MergeResult lineMerge = LineMerge.merge(baseLines, oursLines, theirsLines);

                if (lineMerge.clean) {
                    byte[] mergedData = String.join("\n", lineMerge.mergedLines).getBytes(StandardCharsets.UTF_8);
                    TreeEntryInfo info = writeMergedFile(path, mergedData, oursEntry.mode, cas);
                    mergedFlat.put(path, info);
                } else {
                    clean = false;
                    conflictedPaths.add(path);

                    ConflictNode conflict = new ConflictNode(
                            baseEntry != null ? baseEntry.hash : null,
                            oursEntry.hash,
                            theirsEntry.hash,
                            path
                    );
                    String conflictHash = cas.writeObject(conflict);

                    TreeEntryInfo info = new TreeEntryInfo();
                    info.path = path;
                    info.hash = conflictHash;
                    info.type = ObjectType.CONFLICT;
                    info.mode = oursEntry.mode;
                    mergedFlat.put(path, info);
                }
            } else {
                // Type mismatch or non-file conflicts
                clean = false;
                conflictedPaths.add(path);

                ConflictNode conflict = new ConflictNode(
                        baseEntry != null ? baseEntry.hash : null,
                        oursEntry.hash,
                        theirsEntry.hash,
                        path
                    );
                String conflictHash = cas.writeObject(conflict);

                TreeEntryInfo info = new TreeEntryInfo();
                info.path = path;
                info.hash = conflictHash;
                info.type = ObjectType.CONFLICT;
                info.mode = oursEntry.mode;
                mergedFlat.put(path, info);
            }
        }

        String mergedTreeHash = rebuildTree(mergedFlat.values(), cas);
        return new MergeResult(mergedTreeHash, clean, conflictedPaths);
    }

    private static boolean entriesEqual(TreeEntryInfo a, TreeEntryInfo b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return Objects.equals(a.hash, b.hash) && a.type == b.type && a.mode == b.mode;
    }

    private static boolean isFile(TreeEntryInfo entry) {
        return entry.type == ObjectType.BLOB || entry.type == ObjectType.CHUNK_TREE;
    }

    private static Map<String, TreeEntryInfo> flattenTree(String treeHash, CAS cas) throws IOException {
        Map<String, TreeEntryInfo> flat = new HashMap<>();
        if (treeHash != null) {
            flattenRecursive("", treeHash, cas, flat);
        }
        return flat;
    }

    private static void flattenRecursive(String currentPath, String treeHash, CAS cas, Map<String, TreeEntryInfo> flat) throws IOException {
        Tree tree = (Tree) cas.readObject(treeHash);
        for (TreeEntry entry : tree.getEntries()) {
            String relPath = currentPath.isEmpty() ? entry.getName() : currentPath + "/" + entry.getName();
            if (entry.getType() == ObjectType.TREE) {
                flattenRecursive(relPath, entry.getHash(), cas, flat);
            } else {
                TreeEntryInfo info = new TreeEntryInfo();
                info.path = relPath;
                info.hash = entry.getHash();
                info.type = entry.getType();
                info.mode = entry.getMode();
                flat.put(relPath, info);
            }
        }
    }

    private static byte[] readFileBytes(TreeEntryInfo entry, CAS cas) throws IOException {
        if (entry.type == ObjectType.BLOB) {
            Blob blob = (Blob) cas.readObject(entry.hash);
            return blob.getContent();
        } else if (entry.type == ObjectType.CHUNK_TREE) {
            ChunkTree ct = (ChunkTree) cas.readObject(entry.hash);
            int totalSize = (int) ct.getTotalSize();
            byte[] data = new byte[totalSize];
            int offset = 0;
            for (String chunkHash : ct.getChunkHashes()) {
                Blob chunk = (Blob) cas.readObject(chunkHash);
                byte[] cb = chunk.getContent();
                System.arraycopy(cb, 0, data, offset, cb.length);
                offset += cb.length;
            }
            return data;
        }
        throw new IllegalArgumentException("Cannot read bytes from: " + entry.type);
    }

    private static TreeEntryInfo writeMergedFile(String path, byte[] data, int mode, CAS cas) throws IOException {
        TreeEntryInfo info = new TreeEntryInfo();
        info.path = path;
        info.mode = mode;

        if (data.length > 1024 * 1024) { // > 1MB: chunk tree
            List<FastCDC.Chunk> chunks = FastCDC.chunk(data);
            List<String> chunkHashes = new ArrayList<>();
            List<Integer> chunkSizes = new ArrayList<>();

            for (FastCDC.Chunk chunk : chunks) {
                byte[] cb = chunk.getBytes();
                Blob blob = new Blob(cb);
                chunkHashes.add(cas.writeObject(blob));
                chunkSizes.add(cb.length);
            }

            ChunkTree ct = new ChunkTree(chunkHashes, chunkSizes, data.length);
            info.hash = cas.writeObject(ct);
            info.type = ObjectType.CHUNK_TREE;
        } else {
            Blob blob = new Blob(data);
            info.hash = cas.writeObject(blob);
            info.type = ObjectType.BLOB;
        }
        return info;
    }

    private static List<String> toLines(byte[] data) {
        if (data.length == 0) {
            return new ArrayList<>();
        }
        String s = new String(data, StandardCharsets.UTF_8);
        return new ArrayList<>(Arrays.asList(s.split("\r?\n", -1)));
    }

    private static String rebuildTree(Collection<TreeEntryInfo> entries, CAS cas) throws IOException {
        TreeNode rootNode = new TreeNode("", ObjectType.TREE, 040000);

        for (TreeEntryInfo entry : entries) {
            String[] segments = entry.path.split("/");
            TreeNode current = rootNode;

            for (int i = 0; i < segments.length; i++) {
                String segment = segments[i];
                boolean isLast = (i == segments.length - 1);

                if (isLast) {
                    current.children.put(segment, new TreeNode(segment, entry.type, entry.mode, entry.hash));
                } else {
                    current.children.putIfAbsent(segment, new TreeNode(segment, ObjectType.TREE, 040000));
                    current = current.children.get(segment);
                }
            }
        }

        return writeTreeNode(rootNode, cas);
    }

    private static String writeTreeNode(TreeNode node, CAS cas) throws IOException {
        if (node.type != ObjectType.TREE) {
            return node.hash;
        }

        List<TreeEntry> entries = new ArrayList<>();
        for (TreeNode child : node.children.values()) {
            String childHash = writeTreeNode(child, cas);
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

        TreeNode(String name, ObjectType type, int mode) {
            this.name = name;
            this.type = type;
            this.mode = mode;
        }

        TreeNode(String name, ObjectType type, int mode, String hash) {
            this.name = name;
            this.type = type;
            this.mode = mode;
            this.hash = hash;
        }
    }
}
