package com.draftflow.core;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class ComprehensiveCompressorTest {

    @Test
    public void testCompressDecompressData() {
        byte[] original = "hello compressor test payload 1234567890".getBytes(StandardCharsets.UTF_8);
        byte[] compressed = Compressor.compress(original);
        byte[] decompressed = Compressor.decompress(compressed);

        assertArrayEquals(original, decompressed);
    }

    @Test
    public void testCompressDecompressEmptyData() {
        byte[] original = new byte[0];
        byte[] compressed = Compressor.compress(original);
        byte[] decompressed = Compressor.decompress(compressed);

        assertArrayEquals(original, decompressed);
    }

    @Test
    public void testCompressDecompressLargeData() {
        byte[] original = new byte[100000];
        for (int i = 0; i < original.length; i++) {
            original[i] = (byte) (i % 256);
        }

        byte[] compressed = Compressor.compress(original);
        byte[] decompressed = Compressor.decompress(compressed);

        assertArrayEquals(original, decompressed);
    }

    @Test
    public void testDecompressCorruptedDataThrows() {
        byte[] corrupted = new byte[] { 1, 2, 3, 4, 5, 6 };
        assertThrows(RuntimeException.class, () -> {
            Compressor.decompress(corrupted);
        });
    }
}
