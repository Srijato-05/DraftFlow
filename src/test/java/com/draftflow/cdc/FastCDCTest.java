package com.draftflow.cdc;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class FastCDCTest {

    @Test
    public void testEmptyDataChunking() {
        byte[] empty = new byte[0];
        List<FastCDC.Chunk> chunks = FastCDC.chunk(empty);
        assertEquals(1, chunks.size());
        assertEquals(0, chunks.get(0).getLength());
    }

    @Test
    public void testSmallDataChunking() {
        byte[] small = "Hello World".getBytes();
        List<FastCDC.Chunk> chunks = FastCDC.chunk(small);
        assertEquals(1, chunks.size());
        assertEquals(small.length, chunks.get(0).getLength());
    }

    @Test
    public void testLargeDataChunking() {
        // Generate a 1MB random array
        byte[] large = new byte[1024 * 1024];
        Random rand = new Random(1337);
        rand.nextBytes(large);

        List<FastCDC.Chunk> chunks = FastCDC.chunk(large);
        assertTrue(chunks.size() > 1, "Large data should be split into multiple chunks");

        int totalLength = 0;
        for (FastCDC.Chunk chunk : chunks) {
            assertTrue(chunk.getLength() >= 0, "Chunk length must be positive");
            totalLength += chunk.getLength();
        }
        
        assertEquals(large.length, totalLength, "Total length of chunks should equal original data size");
    }

    @Test
    public void testDeduplicationStability() {
        // Create random base content
        byte[] base = new byte[256 * 1024];
        Random rand = new Random(88);
        rand.nextBytes(base);

        List<FastCDC.Chunk> baseChunks = FastCDC.chunk(base);

        // Create modified content by injecting bytes at the beginning
        byte[] modified = new byte[base.length + 10];
        System.arraycopy("INJECTED10".getBytes(), 0, modified, 0, 10);
        System.arraycopy(base, 0, modified, 10, base.length);

        List<FastCDC.Chunk> modifiedChunks = FastCDC.chunk(modified);

        // With content-defined chunking, many chunks from the middle/end should match exactly in size and content
        int matchingChunks = 0;
        for (int i = 2; i < baseChunks.size(); i++) {
            byte[] baseBytes = baseChunks.get(i).getBytes();
            for (int j = 2; j < modifiedChunks.size(); j++) {
                byte[] modBytes = modifiedChunks.get(j).getBytes();
                if (java.util.Arrays.equals(baseBytes, modBytes)) {
                    matchingChunks++;
                    break;
                }
            }
        }

        assertTrue(matchingChunks > 0, "Content-defined chunking should preserve downstream chunk boundaries and match blocks");
    }
}
