package com.draftflow.core;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CAS {
    private final Path rootDir;
    private final Path draftFlowDir;
    private final Path objectsDir;

    public CAS(Path rootDir) {
        this.rootDir = rootDir;
        this.draftFlowDir = rootDir.resolve(".draftflow");
        this.objectsDir = draftFlowDir.resolve("objects");
    }

    public void init() throws IOException {
        Files.createDirectories(draftFlowDir);
        Files.createDirectories(objectsDir);
        Files.createDirectories(draftFlowDir.resolve("refs").resolve("heads"));
        Files.createDirectories(draftFlowDir.resolve("refs").resolve("changes"));
        Files.createDirectories(draftFlowDir.resolve("index"));
        Files.createDirectories(draftFlowDir.resolve("logs"));

        // Create initial default config if missing
        Path configPath = draftFlowDir.resolve("config.json");
        if (!Files.exists(configPath)) {
            String defaultConfig = "{\n  \"version\": \"1.0\",\n  \"hashAlgorithm\": \"SHA-256\",\n  \"exclude\": [\".git\", \".draftflow\", \"build\", \"out\", \"target\", \".gradle\", \".idea\"]\n}";
            Files.writeString(configPath, defaultConfig);
        }
    }

    public boolean isInitialized() {
        return Files.exists(draftFlowDir) && Files.exists(objectsDir);
    }

    public String writeObject(DraftFlowObject obj) throws IOException {
        byte[] serializedWithHeader = obj.serializeWithHeader();
        String hash = Hasher.hash(serializedWithHeader);
        
        Path objectPath = getObjectPath(hash);
        if (Files.exists(objectPath)) {
            return hash; // Deduplication: already exists on disk
        }

        Files.createDirectories(objectPath.getParent());
        byte[] compressed = Compressor.compress(serializedWithHeader);
        Files.write(objectPath, compressed, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        
        return hash;
    }

    public DraftFlowObject readObject(String hash) throws IOException {
        Path objectPath = getObjectPath(hash);
        if (!Files.exists(objectPath)) {
            throw new IOException("Object not found: " + hash);
        }

        byte[] compressed = Files.readAllBytes(objectPath);
        byte[] decompressed;
        try {
            decompressed = Compressor.decompress(compressed);
        } catch (Exception e) {
            throw new IOException("Decompression failed for: " + hash, e);
        }

        // Parse header: "[type] [size]\0[payload]"
        int spaceIndex = -1;
        int nullIndex = -1;
        
        for (int i = 0; i < decompressed.length; i++) {
            if (decompressed[i] == ' ' && spaceIndex == -1) {
                spaceIndex = i;
            }
            if (decompressed[i] == '\0') {
                nullIndex = i;
                break;
            }
        }

        if (spaceIndex == -1 || nullIndex == -1) {
            throw new IOException("Corrupt object header for: " + hash);
        }

        String typeStr = new String(Arrays.copyOfRange(decompressed, 0, spaceIndex)).toUpperCase();
        ObjectType type = ObjectType.valueOf(typeStr);
        
        byte[] payload = Arrays.copyOfRange(decompressed, nullIndex + 1, decompressed.length);

        return switch (type) {
            case BLOB -> new Blob(payload);
            case TREE -> Tree.deserialize(payload);
            case REVISION -> Revision.deserialize(payload);
            case CONFLICT -> ConflictNode.deserialize(payload);
            case CHUNK_TREE -> ChunkTree.deserialize(payload);
        };
    }

    private Path getObjectPath(String hash) {
        String dir = hash.substring(0, 2);
        String file = hash.substring(2);
        return objectsDir.resolve(dir).resolve(file);
    }

    public Path getRootDir() {
        return rootDir;
    }

    public Path getDraftFlowDir() {
        return draftFlowDir;
    }

    public DraftFlowConfig getConfig() throws IOException {
        return DraftFlowConfig.load(draftFlowDir.resolve("config.json"));
    }

    public String resolveHash(String shortHash) throws IOException {
        if (shortHash == null || shortHash.length() < 4) {
            return null;
        }
        if (shortHash.length() == 64) {
            Path path = getObjectPath(shortHash);
            return Files.exists(path) ? shortHash : null;
        }

        String prefix = shortHash.substring(0, 2);
        String suffix = shortHash.substring(2);
        Path dir = objectsDir.resolve(prefix);
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return null;
        }

        List<String> matches = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                String name = entry.getFileName().toString();
                if (name.startsWith(suffix)) {
                    matches.add(prefix + name);
                }
            }
        }

        if (matches.size() == 1) {
            return matches.get(0);
        } else if (matches.size() > 1) {
            System.err.println("Ambiguous hash: " + shortHash + " matches " + matches);
            return null;
        }
        return null;
    }
}
