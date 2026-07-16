/**
 * @file Compressor.java
 * @description Standard DEFLATE compression engine for DraftFlow VCS objects.
 * Wraps JDK's `Deflater` and `Inflater` to shrink the size of objects serialized in the CAS database.
 * 
 * DESIGN RATIONALE:
 * - Employs GZIP-compatible DEFLATE format to ensure high compression ratios with low CPU overhead.
 * - Incorporates robust cleanup calls (`deflater.end()` / `inflater.end()`) to reclaim off-heap native
 *   ZLIB allocator memory immediately, preventing native memory leaks under heavy load.
 */

package com.draftflow.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class Compressor {

    public static byte[] compress(byte[] data) {
        Deflater deflater = new Deflater();
        deflater.setInput(data);
        deflater.finish();

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length)) {
            byte[] buffer = new byte[8192];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                outputStream.write(buffer, 0, count);
            }
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Compression failed", e);
        } finally {
            deflater.end();
        }
    }

    public static byte[] decompress(byte[] data) {
        Inflater inflater = new Inflater();
        inflater.setInput(data);

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length * 2)) {
            byte[] buffer = new byte[8192];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                outputStream.write(buffer, 0, count);
            }
            return outputStream.toByteArray();
        } catch (IOException | DataFormatException e) {
            throw new RuntimeException("Decompression failed", e);
        } finally {
            inflater.end();
        }
    }
}
