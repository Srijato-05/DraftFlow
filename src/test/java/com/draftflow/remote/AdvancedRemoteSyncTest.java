package com.draftflow.remote;

import com.draftflow.core.Blob;
import com.draftflow.core.CAS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class AdvancedRemoteSyncTest {

    @TempDir
    Path tempDir;

    private CAS localCas;

    @BeforeEach
    public void setUp() throws IOException {
        localCas = new CAS(tempDir);
        localCas.init();
    }

    // A mock client to simulate latency and check OCC behavior
    private static class LatencyMockRemoteClient extends RemoteClient {
        private final Map<String, String> refsStore = new ConcurrentHashMap<>();
        private final AtomicInteger writeAttempts = new AtomicInteger(0);

        LatencyMockRemoteClient() {
            super("file:///mock-remote");
        }

        @Override
        public String getRef(String refName) throws IOException, InterruptedException {
            Thread.sleep(50); // Simulate network read latency
            return refsStore.get(refName);
        }

        @Override
        public void putRef(String refName, String revisionHash) throws IOException, InterruptedException {
            writeAttempts.incrementAndGet();
            Thread.sleep(100); // Simulate network write latency (widening the race window)
            refsStore.put(refName, revisionHash);
        }
    }

    @Test
    public void testOCCConcurrencyRaceCondition() throws InterruptedException, ExecutionException {
        LatencyMockRemoteClient client = new LatencyMockRemoteClient();
        String refName = "heads/main";
        
        // Setup initial ref on remote
        String initialHash = "initialhash1234567890abcdef1234567890abcdef1234567890abcdef1234";
        client.refsStore.put(refName, initialHash);

        int numThreads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < numThreads; i++) {
            final String targetHash = "threadhash-" + i + "-abcdef1234567890abcdef1234567890abcdef12345";
            futures.add(executor.submit(() -> {
                try {
                    return OCC.tryUpdateRef(client, refName, initialHash, targetHash);
                } catch (OCC.ConcurrencyException e) {
                    return false;
                }
            }));
        }

        int successCount = 0;
        int failureCount = 0;

        for (Future<Boolean> f : futures) {
            if (f.get()) {
                successCount++;
            } else {
                failureCount++;
            }
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        // OCC must ensure that at most 1 write succeeds, and other concurrent updates are rejected
        assertEquals(1, successCount, "Only one thread should successfully update the ref");
        assertEquals(numThreads - 1, failureCount, "All other competing threads must fail with ConcurrencyException");
    }

    @Test
    public void testMassivePackingAndUnpackingOffsetIntegrity() throws IOException {
        List<String> writtenHashes = new ArrayList<>();
        
        // Write 200 objects to local CAS
        for (int i = 0; i < 200; i++) {
            Blob b = new Blob(("Content number: " + i).getBytes());
            String hash = localCas.writeObject(b);
            writtenHashes.add(hash);
        }

        // Pack all 200 objects
        ByteArrayOutputStream packOut = new ByteArrayOutputStream();
        Packer.createPack(writtenHashes, localCas, packOut);

        byte[] packBytes = packOut.toByteArray();
        assertTrue(packBytes.length > 0);
        
        // Verify packfile magic header prefix 'DFPACK'
        String headerPrefix = new String(packBytes, 0, 6);
        assertEquals("DFPACK", headerPrefix);

        // Unpack into a clean CAS directory
        Path destDir = tempDir.resolve("dest-cas");
        CAS destCas = new CAS(destDir);
        destCas.init();

        ByteArrayInputStream packIn = new ByteArrayInputStream(packBytes);
        List<String> unpackedHashes = Packer.unpack(packIn, destCas);

        assertEquals(writtenHashes.size(), unpackedHashes.size());
        
        // Assert all objects are unpacked correctly and match original contents
        for (int i = 0; i < 200; i++) {
            String originalHash = writtenHashes.get(i);
            assertTrue(unpackedHashes.contains(originalHash));
            
            Blob b = (Blob) destCas.readObject(originalHash);
            assertEquals("Content number: " + i, new String(b.getContent()));
        }
    }

    @Test
    public void testNetworkErrorDuringUnpackingRollback() throws IOException {
        // Prepare some pack content
        List<String> hashes = new ArrayList<>();
        Blob b1 = new Blob("Data 1".getBytes());
        Blob b2 = new Blob("Data 2".getBytes());
        hashes.add(localCas.writeObject(b1));
        hashes.add(localCas.writeObject(b2));

        ByteArrayOutputStream packOut = new ByteArrayOutputStream();
        Packer.createPack(hashes, localCas, packOut);
        byte[] packBytes = packOut.toByteArray();

        // Create a custom InputStream that fails half-way through reading payload
        InputStream corruptIn = new InputStream() {
            private int index = 0;
            @Override
            public int read() throws IOException {
                if (index > 40) { // Fail after reading the header and some metadata
                    throw new IOException("Simulated Connection Timeout / Drop");
                }
                if (index >= packBytes.length) {
                    return -1;
                }
                return packBytes[index++] & 0xFF;
            }
        };

        Path destDir = tempDir.resolve("dest-cas-fail");
        CAS destCas = new CAS(destDir);
        destCas.init();

        // Unpacking must fail with our simulated exception
        assertThrows(IOException.class, () -> Packer.unpack(corruptIn, destCas));
    }
}
