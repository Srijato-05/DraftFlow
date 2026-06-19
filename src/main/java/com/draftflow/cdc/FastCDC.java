package com.draftflow.cdc;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class FastCDC {
    private static final int MIN_CHUNK_SIZE = 8192;    // 8 KB
    private static final int MAX_CHUNK_SIZE = 65536;   // 64 KB

    // Gear hash table of 256 random integers
    private static final int[] GEAR_TABLE = new int[256];
    static {
        Random r = new Random(42); // Seeded for determinism
        for (int i = 0; i < 256; i++) {
            GEAR_TABLE[i] = r.nextInt();
        }
    }

    public static class Chunk {
        private final byte[] data;
        private final int offset;
        private final int length;
        private String hash; // Calculated later if needed

        public Chunk(byte[] data, int offset, int length) {
            this.data = data;
            this.offset = offset;
            this.length = length;
        }

        public byte[] getBytes() {
            if (offset == 0 && length == data.length) {
                return data;
            }
            byte[] copy = new byte[length];
            System.arraycopy(data, offset, copy, 0, length);
            return copy;
        }

        public int getLength() {
            return length;
        }

        public String getHash() {
            return hash;
        }

        public void setHash(String hash) {
            this.hash = hash;
        }
    }

    /**
     * Splits data into a list of variable-sized chunks.
     */
    public static List<Chunk> chunk(byte[] data) {
        List<Chunk> chunks = new ArrayList<>();
        int n = data.length;
        if (n == 0) {
            chunks.add(new Chunk(data, 0, 0));
            return chunks;
        }

        int offset = 0;
        // Target 16KB: mask has 14 bits set (e.g. 0x00003FFF).
        // Standard FastCDC uses two different masks depending on the index to normalize size.
        // For simplicity, we use mask = 0x00003fff.
        int mask = 0x00003FFF;

        while (offset < n) {
            int remaining = n - offset;
            if (remaining <= MIN_CHUNK_SIZE) {
                chunks.add(new Chunk(data, offset, remaining));
                break;
            }

            int chunkSize = MIN_CHUNK_SIZE;
            int hash = 0;

            // Search for chunk boundary between MIN_CHUNK_SIZE and MAX_CHUNK_SIZE
            int limit = Math.min(remaining, MAX_CHUNK_SIZE);
            for (int i = MIN_CHUNK_SIZE; i < limit; i++) {
                int byteVal = data[offset + i] & 0xFF;
                hash = (hash << 1) + GEAR_TABLE[byteVal];
                
                if ((hash & mask) == 0) {
                    chunkSize = i + 1;
                    break;
                }
            }

            // If we exceeded MAX_CHUNK_SIZE without a boundary, split at MAX_CHUNK_SIZE
            if (chunkSize == MIN_CHUNK_SIZE && limit == MAX_CHUNK_SIZE) {
                chunkSize = MAX_CHUNK_SIZE;
            }

            chunks.add(new Chunk(data, offset, chunkSize));
            offset += chunkSize;
        }

        return chunks;
    }
}
