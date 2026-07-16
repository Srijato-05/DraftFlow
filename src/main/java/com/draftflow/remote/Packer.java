/**
 * @file Packer.java
 * @description Archive packaging and unpacking manager for DraftFlow VCS sync operations.
 * Consolidates multiple compressed object files into a single binary pack stream (`.dfpack`)
 * containing custom headers, lookup indices, and offset payloads.
 * 
 * DESIGN RATIONALE:
 * - Sending thousands of tiny, separate file fragments over HTTP is highly inefficient.
 * - This engine packs selected object hashes into a single file payload prefixed with `DFPACK`.
 * - Includes a custom metadata header indexing hashes, offsets, and sizes. This enables
 *   the receiver to unpack the stream directly in linear time.
 */

package com.draftflow.remote;

import com.draftflow.core.CAS;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class Packer {

    private static final String PACK_HEADER = "DFPACK";

    public static void createPack(List<String> hashes, CAS cas, OutputStream out) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);
        dos.write(PACK_HEADER.getBytes(StandardCharsets.UTF_8));
        dos.writeInt(hashes.size());

        long currentOffset = 0;
        List<PackEntry> entries = new ArrayList<>();
        ByteArrayOutputStream payloadStream = new ByteArrayOutputStream();

        for (String hash : hashes) {
            Path objectPath = cas.getDraftFlowDir().resolve("objects").resolve(hash.substring(0, 2)).resolve(hash.substring(2));
            if (!Files.exists(objectPath)) {
                throw new FileNotFoundException("Object not found: " + hash);
            }
            byte[] compressedBytes = Files.readAllBytes(objectPath);
            int size = compressedBytes.length;
            entries.add(new PackEntry(hash, currentOffset, size));
            payloadStream.write(compressedBytes);
            currentOffset += size;
        }

        // Write index header
        for (PackEntry entry : entries) {
            dos.writeUTF(entry.hash);
            dos.writeLong(entry.offset);
            dos.writeInt(entry.size);
        }

        // Write payload
        dos.write(payloadStream.toByteArray());
        dos.flush();
    }

    public static List<String> unpack(InputStream in, CAS cas) throws IOException {
        DataInputStream dis = new DataInputStream(in);
        byte[] headerBytes = new byte[6];
        dis.readFully(headerBytes);
        String header = new String(headerBytes, StandardCharsets.UTF_8);
        if (!header.equals(PACK_HEADER)) {
            throw new IOException("Invalid packfile header: " + header);
        }

        int numObjects = dis.readInt();
        List<PackEntry> entries = new ArrayList<>();
        for (int i = 0; i < numObjects; i++) {
            String hash = dis.readUTF();
            long offset = dis.readLong();
            int size = dis.readInt();
            entries.add(new PackEntry(hash, offset, size));
        }

        // Now read payload
        List<String> unpackedHashes = new ArrayList<>();
        for (PackEntry entry : entries) {
            byte[] data = new byte[entry.size];
            dis.readFully(data);
            
            // Write directly to local CAS
            String hash = entry.hash;
            Path objectPath = cas.getDraftFlowDir().resolve("objects").resolve(hash.substring(0, 2)).resolve(hash.substring(2));
            if (!Files.exists(objectPath)) {
                Files.createDirectories(objectPath.getParent());
                Files.write(objectPath, data);
            }
            unpackedHashes.add(hash);
        }
        return unpackedHashes;
    }

    private static class PackEntry {
        String hash;
        long offset;
        int size;

        PackEntry(String hash, long offset, int size) {
            this.hash = hash;
            this.offset = offset;
            this.size = size;
        }
    }
}
