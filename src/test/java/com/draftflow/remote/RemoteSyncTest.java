package com.draftflow.remote;

import com.draftflow.core.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class RemoteSyncTest {

    @TempDir
    Path localDir1;

    @TempDir
    Path localDir2;

    @TempDir
    Path remoteDir;

    @Test
    public void testEndToEndSync() throws Exception {
        // --- 1. SETUP REPO 1 ---
        CAS cas1 = new CAS(localDir1);
        cas1.init();

        Blob b1 = new Blob("Hello Remote Sync".getBytes());
        String b1Hash = cas1.writeObject(b1);

        TreeEntry entry = new TreeEntry("hello.txt", b1Hash, ObjectType.BLOB, 100644);
        Tree tree = new Tree(Arrays.asList(entry));
        String treeHash = cas1.writeObject(tree);

        Revision rev = new Revision(
                treeHash,
                Arrays.asList(),
                "change-id-123",
                "tester",
                System.currentTimeMillis(),
                "Initial Commit",
                false
        );
        String revHash = cas1.writeObject(rev);

        // --- 2. PACKING ---
        List<String> hashes = Arrays.asList(b1Hash, treeHash, revHash);
        ByteArrayOutputStream packOut = new ByteArrayOutputStream();
        Packer.createPack(hashes, cas1, packOut);
        byte[] packData = packOut.toByteArray();

        // --- 3. PUSH VIA REMOTE CLIENT ---
        String remoteUrl = "file://" + remoteDir.toAbsolutePath().toString().replace("\\", "/");
        RemoteClient client = new RemoteClient(remoteUrl);

        // Upload pack
        String packId = "pack-1";
        client.uploadPack(packId, packData);

        // Upload index mapping
        Map<String, String> objectMap = new HashMap<>();
        for (String h : hashes) {
            objectMap.put(h, packId);
        }
        client.uploadIndex(objectMap);

        // Update branch ref under OCC
        boolean updateSuccess = OCC.tryUpdateRef(client, "heads/main", null, revHash);
        assertTrue(updateSuccess);

        // Verify remote ref value
        String remoteHead = client.getRef("heads/main");
        assertEquals(revHash, remoteHead);

        // Verify OCC concurrency conflict detection
        assertThrows(OCC.ConcurrencyException.class, () -> {
            OCC.tryUpdateRef(client, "heads/main", null, "some-other-hash");
        });

        // --- 4. SETUP REPO 2 & PULL ---
        CAS cas2 = new CAS(localDir2);
        cas2.init();

        // Remote client for Repo 2
        RemoteClient client2 = new RemoteClient(remoteUrl);
        String pulledHead = client2.getRef("heads/main");
        assertEquals(revHash, pulledHead);

        Map<String, String> remoteIndex = client2.downloadIndex();
        assertTrue(remoteIndex.containsKey(revHash));

        // Pull and unpack missing objects
        String packToDownload = remoteIndex.get(pulledHead);
        byte[] pulledPackData = client2.downloadPack(packToDownload);

        ByteArrayInputStream packIn = new ByteArrayInputStream(pulledPackData);
        List<String> unpacked = Packer.unpack(packIn, cas2);

        assertTrue(unpacked.contains(b1Hash));
        assertTrue(unpacked.contains(treeHash));
        assertTrue(unpacked.contains(revHash));

        // Verify objects are readable in local CAS 2
        DraftFlowObject retrievedBlob = cas2.readObject(b1Hash);
        assertEquals(ObjectType.BLOB, retrievedBlob.getType());
        assertEquals("Hello Remote Sync", new String(((Blob) retrievedBlob).getContent()));

        DraftFlowObject retrievedRev = cas2.readObject(pulledHead);
        assertEquals(ObjectType.REVISION, retrievedRev.getType());
        assertEquals("change-id-123", ((Revision) retrievedRev).getChangeId());
    }
}
