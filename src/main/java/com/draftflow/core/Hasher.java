/**
 * @file Hasher.java
 * @description Hash calculation utilities for DraftFlow VCS object IDs.
 * Encapsulates JDK standard `MessageDigest` configurations to generate SHA-256 checksums from binary payloads.
 * 
 * DESIGN RATIONALE:
 * - Employs SHA-256 as the standard content hash algorithm to guarantee high collision resistance,
 *   supporting the integrity of Content Addressable Storage (CAS) indexing.
 * - Formats result digests as 64-character lowercase hexadecimal strings for consistent filesystem naming.
 */

package com.draftflow.core;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Hasher {
    
    private Hasher() {}
    
    public static String hash(byte[] data) {
        return hash(data, "SHA-256");
    }

    public static String hash(byte[] data, String algorithm) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] hashBytes = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Algorithm not found", e);
        }
    }

    public static String hash(String text) {
        return hash(text.getBytes());
    }
}
