/**
 * @file BinaryDelta.java
 * @description Binary delta compression engine for DraftFlow VCS.
 * Computes difference deltas between a base byte array and a target byte array.
 * Employs COPY and INSERT commands to reconstruct target versions from their bases.
 * 
 * DESIGN RATIONALE:
 * - Storing full versions of slightly modified files wastes storage space.
 * - This engine indexes 16-byte blocks of the base file into a hash map.
 * - When scanning the target, matches are coded as a COPY command (offset + length).
 *   New/inserted bytes are coded as an INSERT command (raw bytes).
 */

package com.draftflow.core;

import java.io.*;
import java.util.*;

public class BinaryDelta {

    private static final int BLOCK_SIZE = 16;
    private static final byte CMD_COPY = 0x01;
    private static final byte CMD_INSERT = 0x02;

    public static byte[] compress(byte[] base, byte[] target) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(out);

        // 1. Index 16-byte blocks in the base file
        Map<Integer, List<Integer>> blockIndex = new HashMap<>();
        for (int i = 0; i <= base.length - BLOCK_SIZE; i += BLOCK_SIZE) {
            int hash = blockHash(base, i);
            blockIndex.computeIfAbsent(hash, k -> new ArrayList<>()).add(i);
        }

        int t = 0;
        ByteArrayOutputStream insertBuf = new ByteArrayOutputStream();

        while (t < target.length) {
            boolean matchFound = false;
            int bestMatchOffset = -1;
            int bestMatchLen = 0;

            if (t <= target.length - BLOCK_SIZE) {
                int targetHash = blockHash(target, t);
                List<Integer> offsets = blockIndex.get(targetHash);
                if (offsets != null) {
                    for (int offset : offsets) {
                        int len = 0;
                        while (offset + len < base.length && t + len < target.length 
                               && base[offset + len] == target[t + len]) {
                            len++;
                        }
                        if (len >= BLOCK_SIZE && len > bestMatchLen) {
                            bestMatchLen = len;
                            bestMatchOffset = offset;
                            matchFound = true;
                        }
                    }
                }
            }

            if (matchFound) {
                if (insertBuf.size() > 0) {
                    dos.writeByte(CMD_INSERT);
                    dos.writeInt(insertBuf.size());
                    dos.write(insertBuf.toByteArray());
                    insertBuf.reset();
                }

                dos.writeByte(CMD_COPY);
                dos.writeInt(bestMatchOffset);
                dos.writeInt(bestMatchLen);

                t += bestMatchLen;
            } else {
                insertBuf.write(target[t]);
                t++;
            }
        }

        if (insertBuf.size() > 0) {
            dos.writeByte(CMD_INSERT);
            dos.writeInt(insertBuf.size());
            dos.write(insertBuf.toByteArray());
        }

        dos.flush();
        return out.toByteArray();
    }

    public static byte[] decompress(byte[] base, byte[] delta) throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(delta);
        DataInputStream dis = new DataInputStream(in);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        while (dis.available() > 0) {
            byte cmd = dis.readByte();
            if (cmd == CMD_COPY) {
                int offset = dis.readInt();
                int len = dis.readInt();
                if (offset < 0 || offset + len > base.length) {
                    throw new IOException("Malformed delta: copy out of bounds");
                }
                out.write(base, offset, len);
            } else if (cmd == CMD_INSERT) {
                int len = dis.readInt();
                byte[] literal = new byte[len];
                dis.readFully(literal);
                out.write(literal);
            } else {
                throw new IOException("Unknown delta command: " + cmd);
            }
        }

        return out.toByteArray();
    }

    private static int blockHash(byte[] data, int start) {
        int hash = 1;
        for (int i = 0; i < BLOCK_SIZE; i++) {
            hash = 31 * hash + data[start + i];
        }
        return hash;
    }
}
