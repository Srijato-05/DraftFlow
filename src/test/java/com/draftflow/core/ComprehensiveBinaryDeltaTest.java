package com.draftflow.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class ComprehensiveBinaryDeltaTest {

    @Test
    public void testCompressDecompressIdentical() throws IOException {
        byte[] data = "this is a test block of text that is longer than sixteen bytes".getBytes(StandardCharsets.UTF_8);
        byte[] delta = BinaryDelta.compress(data, data);
        byte[] decompressed = BinaryDelta.decompress(data, delta);
        assertArrayEquals(data, decompressed);
    }

    @Test
    public void testCompressDecompressEmpty() throws IOException {
        byte[] empty = new byte[0];
        byte[] delta = BinaryDelta.compress(empty, empty);
        byte[] decompressed = BinaryDelta.decompress(empty, delta);
        assertEquals(0, decompressed.length);
    }

    @Test
    public void testCompressDecompressChanges() throws IOException {
        byte[] base = "abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8);
        // Modify target to insert "HELLO" in the middle
        byte[] target = "abcdefghijklmnopqrstuvwxyz12345HELLO67890abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8);

        byte[] delta = BinaryDelta.compress(base, target);
        byte[] decompressed = BinaryDelta.decompress(base, delta);
        assertArrayEquals(target, decompressed);
    }

    @Test
    public void testMalformedDeltaOutOfBounds() {
        byte[] base = "abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8);
        // Create manually malformed delta bytes
        // CMD_COPY (0x01), offset = 10, len = 20 (base length is 26, so 10 + 20 = 30 > 26)
        byte[] malformedDelta = {
                0x01, // CMD_COPY
                0, 0, 0, 10, // offset = 10
                0, 0, 0, 20  // len = 20
        };

        assertThrows(IOException.class, () -> {
            BinaryDelta.decompress(base, malformedDelta);
        });
    }

    @Test
    public void testMalformedDeltaInvalidCommand() {
        byte[] base = "abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8);
        // CMD = 0x03 (invalid)
        byte[] malformedDelta = { 0x03 };

        assertThrows(IOException.class, () -> {
            BinaryDelta.decompress(base, malformedDelta);
        });
    }
}
