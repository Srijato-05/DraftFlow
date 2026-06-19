package com.draftflow.remote;

import java.io.IOException;

public class OCC {

    public static class ConcurrencyException extends Exception {
        public ConcurrencyException(String message) {
            super(message);
        }
    }

    public static boolean tryUpdateRef(RemoteClient client, String refName, String expectedOldHash, String newHash) 
            throws IOException, InterruptedException, ConcurrencyException {
        
        String currentHash = client.getRef(refName);
        
        if (expectedOldHash == null) {
            if (currentHash != null) {
                throw new ConcurrencyException("Ref " + refName + " already exists on remote with hash " + currentHash + ", expected empty.");
            }
        } else {
            if (currentHash == null || !currentHash.equals(expectedOldHash)) {
                throw new ConcurrencyException("Ref " + refName + " has changed on remote. Current: " + currentHash + ", Expected: " + expectedOldHash);
            }
        }

        // Put the ref
        client.putRef(refName, newHash);

        // Verification check (atomic read-back validation)
        String verifiedHash = client.getRef(refName);
        if (verifiedHash == null || !verifiedHash.equals(newHash)) {
            throw new ConcurrencyException("Verification failed: Remote ref update lost to a concurrent write.");
        }

        return true;
    }
}
