package com.draftflow.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class AdvancedCASTest {

    @TempDir
    Path tempDir;

    private CAS cas;

    @BeforeEach
    public void setUp() throws IOException {
        cas = new CAS(tempDir);
        cas.init();
    }

    @Test
    public void testEmptyBlobStorage() throws IOException {
        // Edge case: Empty byte array
        Blob emptyBlob = new Blob(new byte[0]);
        String hash = cas.writeObject(emptyBlob);
        assertNotNull(hash);
        assertEquals(64, hash.length());

        DraftFlowObject retrieved = cas.readObject(hash);
        assertEquals(ObjectType.BLOB, retrieved.getType());
        assertEquals(0, ((Blob) retrieved).getContent().length);
    }

    @Test
    public void testConcurrentWrites() throws InterruptedException, ExecutionException, IOException {
        // High-concurrency writing to CAS
        int threadCount = 10;
        int objectsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<List<String>>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            futures.add(executor.submit(() -> {
                List<String> hashes = new ArrayList<>();
                for (int j = 0; j < objectsPerThread; j++) {
                    String content = "Thread-" + threadId + "-Object-" + j;
                    Blob blob = new Blob(content.getBytes());
                    String hash = cas.writeObject(blob);
                    hashes.add(hash);
                }
                return hashes;
            }));
        }

        List<List<String>> allHashes = new ArrayList<>();
        for (Future<List<String>> f : futures) {
            allHashes.add(f.get());
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        // Verify all concurrently written blobs are readable and correct
        for (int i = 0; i < threadCount; i++) {
            List<String> threadHashes = allHashes.get(i);
            for (int j = 0; j < objectsPerThread; j++) {
                String expectedContent = "Thread-" + i + "-Object-" + j;
                String hash = threadHashes.get(j);
                DraftFlowObject obj = cas.readObject(hash);
                assertEquals(ObjectType.BLOB, obj.getType());
                assertEquals(expectedContent, new String(((Blob) obj).getContent()));
            }
        }
    }

    @Test
    public void testCorruptedBlobDetection() throws IOException {
        // Write a blob
        Blob blob = new Blob("Corruption Target Content".getBytes());
        String hash = cas.writeObject(blob);

        // Access the underlying file and corrupt it
        String dir = hash.substring(0, 2);
        String file = hash.substring(2);
        Path objectPath = tempDir.resolve(".draftflow").resolve("objects").resolve(dir).resolve(file);
        assertTrue(Files.exists(objectPath));

        // Corrupt by writing junk bytes
        Files.writeString(objectPath, "THIS IS CORRUPTED JUNK DATA THAT CANNOT BE DECOMPRESSED");

        // Attempting to read should throw IOException due to inflate failure
        assertThrows(IOException.class, () -> cas.readObject(hash));
    }

    @Test
    public void testShortHashCollision() throws IOException {
        // We want to simulate two entries having same short hash (e.g. sharing first 4 characters)
        // Since hashes are SHA-256 and hard to collide naturally on first 4 chars for few objects,
        // we can write two actual blobs, find their hashes, and see if we can query.
        // Or we can manually mock/write colliding structures in the objects directory.
        // Let's manually create two files under objects/ab/ that start with the same suffix prefix
        
        String prefix = "ab";
        String suffix1 = "1234abcd567890abcdef1234567890abcdef1234567890abcdef12345678";
        String suffix2 = "1234ffff567890abcdef1234567890abcdef1234567890abcdef12345678";
        
        Path dirPath = tempDir.resolve(".draftflow").resolve("objects").resolve(prefix);
        Files.createDirectories(dirPath);
        
        // Write dummy compressed objects
        Blob b = new Blob("Colliding 1".getBytes());
        byte[] compressed = Compressor.compress(b.serializeWithHeader());
        
        Files.write(dirPath.resolve(suffix1), compressed);
        Files.write(dirPath.resolve(suffix2), compressed);
        
        // Querying for "ab1234" should return null (ambiguous match) because both suffixes start with "1234"
        String resolved = cas.resolveHash("ab1234");
        assertNull(resolved);

        // Querying for "ab1234ab" should resolve to suffix1
        String resolved1 = cas.resolveHash("ab1234ab");
        assertEquals(prefix + suffix1, resolved1);

        // Querying for "ab1234ff" should resolve to suffix2
        String resolved2 = cas.resolveHash("ab1234ff");
        assertEquals(prefix + suffix2, resolved2);
    }
}
