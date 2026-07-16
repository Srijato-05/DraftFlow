package com.draftflow.cdc;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ComprehensiveFastCDCTest {

    @Test
    public void testEmptyData() {
        byte[] data = new byte[0];
        List<FastCDC.Chunk> chunks = FastCDC.chunk(data);
        assertEquals(1, chunks.size());
        assertEquals(0, chunks.get(0).getLength());
        assertArrayEquals(data, chunks.get(0).getBytes());
    }

    @Test
    public void testSmallData() {
        byte[] data = new byte[100];
        List<FastCDC.Chunk> chunks = FastCDC.chunk(data);
        assertEquals(1, chunks.size());
        assertEquals(100, chunks.get(0).getLength());
        assertArrayEquals(data, chunks.get(0).getBytes());
    }

    @Test
    public void testLargeData() {
        // Create 200 KB of dummy data
        byte[] data = new byte[204800];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 256);
        }

        List<FastCDC.Chunk> chunks = FastCDC.chunk(data);
        assertTrue(chunks.size() > 1);

        int totalSize = 0;
        for (int i = 0; i < chunks.size(); i++) {
            FastCDC.Chunk chunk = chunks.get(i);
            int len = chunk.getLength();
            totalSize += len;

            // The last chunk can be smaller than MIN_CHUNK_SIZE if it's the remainder
            if (i < chunks.size() - 1) {
                assertTrue(len >= 8192, "Chunk is too small: " + len);
                assertTrue(len <= 65536, "Chunk is too large: " + len);
            }
        }
        assertEquals(data.length, totalSize);
    }

    @Test
    public void testChunkGettersSetters() {
        byte[] data = new byte[20];
        for (int i = 0; i < 20; i++) data[i] = (byte) i;

        FastCDC.Chunk chunk = new FastCDC.Chunk(data, 5, 10);
        assertEquals(10, chunk.getLength());

        byte[] bytes = chunk.getBytes();
        assertEquals(10, bytes.length);
        assertEquals(5, bytes[0]);
        assertEquals(14, bytes[9]);

        assertNull(chunk.getHash());
        chunk.setHash("testhash123");
        assertEquals("testhash123", chunk.getHash());
    }

    @Test
    public void testDeterminism() {
        byte[] data = new byte[150000];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i * 31);
        }

        List<FastCDC.Chunk> chunks1 = FastCDC.chunk(data);
        List<FastCDC.Chunk> chunks2 = FastCDC.chunk(data);

        assertEquals(chunks1.size(), chunks2.size());
        for (int i = 0; i < chunks1.size(); i++) {
            assertEquals(chunks1.get(i).getLength(), chunks2.get(i).getLength());
            assertArrayEquals(chunks1.get(i).getBytes(), chunks2.get(i).getBytes());
        }
    }
}
