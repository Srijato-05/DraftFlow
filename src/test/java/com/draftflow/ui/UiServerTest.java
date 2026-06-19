package com.draftflow.ui;

import com.draftflow.core.CAS;
import com.draftflow.db.MetadataStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

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
}
