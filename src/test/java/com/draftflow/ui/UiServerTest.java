package com.draftflow.ui;

import com.draftflow.core.CAS;
import com.draftflow.core.Revision;
import com.draftflow.core.Tree;
import com.draftflow.db.MetadataStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class UiServerTest {

    @TempDir
    Path tempDir;

    @Test
    public void testUiServerEndpoints() throws Exception {
        CAS cas = new CAS(tempDir);
        cas.init();

        Path dbPath = tempDir.resolve(".draftflow").resolve("index").resolve("index.mv.db");
        try (MetadataStore db = new MetadataStore(dbPath)) {
            db.open();
            db.setConfig("activeHead", "heads/main");
            db.commit();

            UiServer server = new UiServer(cas, db, 0); // Bind dynamically to a free port
            server.start();
            int port = server.getPort();
            assertTrue(port > 0);

            HttpClient client = HttpClient.newHttpClient();

            // 1. Test index page
            HttpRequest requestIndex = HttpRequest.newBuilder().uri(URI.create("http://localhost:" + port + "/")).GET().build();
            HttpResponse<String> responseIndex = client.send(requestIndex, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, responseIndex.statusCode());
            assertTrue(responseIndex.body().toUpperCase().contains("DRAFTFLOW"));

            // 2. Test status API
            HttpRequest requestStatus = HttpRequest.newBuilder().uri(URI.create("http://localhost:" + port + "/api/status")).GET().build();
            HttpResponse<String> responseStatus = client.send(requestStatus, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, responseStatus.statusCode());
            assertTrue(responseStatus.body().contains("\"activeHead\":\"main\""));

            // 3. Test DAG API
            HttpRequest requestDag = HttpRequest.newBuilder().uri(URI.create("http://localhost:" + port + "/api/dag")).GET().build();
            HttpResponse<String> responseDag = client.send(requestDag, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, responseDag.statusCode());
            assertEquals("[]", responseDag.body().trim());

            server.stop();
        }
    }

    @Test
    public void testUiServerWithPopulatedData() throws Exception {
        CAS cas = new CAS(tempDir);
        cas.init();

        Path dbPath = tempDir.resolve(".draftflow").resolve("index").resolve("index.mv.db");
        try (MetadataStore db = new MetadataStore(dbPath)) {
            db.open();

            // 1. Write mock revision history
            Tree tree = new Tree(Collections.emptyList());
            String treeHash = cas.writeObject(tree);

            Revision r1 = new Revision(treeHash, Collections.emptyList(), "change-1111", "Author A", System.currentTimeMillis() - 10000, "Initial commit message", false);
            String r1Hash = cas.writeObject(r1);

            Revision r2 = new Revision(treeHash, Arrays.asList(r1Hash), "change-1111", "Author A", System.currentTimeMillis() - 5000, "Second commit message", false);
            String r2Hash = cas.writeObject(r2);

            db.setRef("heads/main", r2Hash);
            db.setRef("heads/feature", r1Hash);
            db.setConfig("activeHead", "heads/main");
            db.setConfig("activeRevisionHash", r2Hash);
            db.setConfig("activeChangeId", "change-1111");

            // 2. Put modified, deleted, and conflict files
            // Deleted file (exists in DB metadata but not on disk)
            com.draftflow.db.FileMetadata delMeta = new com.draftflow.db.FileMetadata(
                "deleted.txt", 100L, System.currentTimeMillis(), "hash-del", "BLOB", 0644
            );
            db.putFile(delMeta);

            // Modified file (exists in DB and disk but size/timestamp differs)
            Path modPath = tempDir.resolve("modified.txt");
            Files.writeString(modPath, "New Content");
            com.draftflow.db.FileMetadata modMeta = new com.draftflow.db.FileMetadata(
                "modified.txt", 5L, 0L, "hash-mod", "BLOB", 0644
            );
            db.putFile(modMeta);

            // Conflict file (must exist on disk to not be counted as deleted)
            Path conPath = tempDir.resolve("conflict.txt");
            Files.writeString(conPath, "Conflict Content");
            com.draftflow.db.FileMetadata conMeta = new com.draftflow.db.FileMetadata(
                "conflict.txt", 20L, System.currentTimeMillis(), "hash-con", "CONFLICT", 0644
            );
            db.putFile(conMeta);

            db.commit();

            // 3. Start server
            UiServer server = new UiServer(cas, db, 0);
            server.start();
            int port = server.getPort();

            HttpClient client = HttpClient.newHttpClient();

            // Test DAG API
            HttpRequest requestDag = HttpRequest.newBuilder().uri(URI.create("http://localhost:" + port + "/api/dag")).GET().build();
            HttpResponse<String> responseDag = client.send(requestDag, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, responseDag.statusCode());
            String bodyDag = responseDag.body();
            assertTrue(bodyDag.contains(r1Hash));
            assertTrue(bodyDag.contains(r2Hash));
            assertTrue(bodyDag.contains("Initial commit message"));
            assertTrue(bodyDag.contains("Second commit message"));
            assertTrue(bodyDag.contains("heads/main"));
            assertTrue(bodyDag.contains("heads/feature"));

            // Test Status API
            HttpRequest requestStatus = HttpRequest.newBuilder().uri(URI.create("http://localhost:" + port + "/api/status")).GET().build();
            HttpResponse<String> responseStatus = client.send(requestStatus, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, responseStatus.statusCode());
            String bodyStatus = responseStatus.body();
            assertTrue(bodyStatus.contains("\"activeHead\":\"main\""));
            assertTrue(bodyStatus.contains("\"modified\":[\"modified.txt\"]"));
            assertTrue(bodyStatus.contains("\"deleted\":[\"deleted.txt\"]"));
            assertTrue(bodyStatus.contains("\"conflicts\":[\"conflict.txt\"]"));

            // Test Ledger API
            HttpRequest requestLedger = HttpRequest.newBuilder().uri(URI.create("http://localhost:" + port + "/api/ledger")).GET().build();
            HttpResponse<String> responseLedger = client.send(requestLedger, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, responseLedger.statusCode());

            // Test Action API (invalid method GET)
            HttpRequest requestActionGet = HttpRequest.newBuilder().uri(URI.create("http://localhost:" + port + "/api/action?cmd=undo")).GET().build();
            HttpResponse<String> responseActionGet = client.send(requestActionGet, HttpResponse.BodyHandlers.ofString());
            assertEquals(405, responseActionGet.statusCode());

            server.stop();
        }
    }
}
