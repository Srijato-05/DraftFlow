package com.draftflow.remote;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class RemoteClientHttpTest {

    private static HttpServer server;
    private static int port;
    private static final Map<String, String> refs = new HashMap<>();
    private static final Map<String, byte[]> packs = new HashMap<>();
    private static String packIndex = "";
    
    private static int refsGetAttempts = 0;
    private static int refsGetStatusToReturn = 200;

    @BeforeAll
    public static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        
        server.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                try {
                    String path = exchange.getRequestURI().getPath();
                    String method = exchange.getRequestMethod();
                    
                    if (path.startsWith("/refs/")) {
                        String refName = path.substring(6);
                        if (method.equals("GET")) {
                            refsGetAttempts++;
                            if (refsGetStatusToReturn != 200) {
                                exchange.sendResponseHeaders(refsGetStatusToReturn, -1);
                                return;
                            }
                            if (refs.containsKey(refName)) {
                                byte[] res = refs.get(refName).getBytes(StandardCharsets.UTF_8);
                                exchange.sendResponseHeaders(200, res.length);
                                try (OutputStream os = exchange.getResponseBody()) {
                                    os.write(res);
                                }
                            } else {
                                exchange.sendResponseHeaders(404, -1);
                            }
                        } else if (method.equals("PUT")) {
                            byte[] body = exchange.getRequestBody().readAllBytes();
                            refs.put(refName, new String(body, StandardCharsets.UTF_8));
                            exchange.sendResponseHeaders(200, -1);
                        }
                    } else if (path.startsWith("/packs/")) {
                        String packId = path.substring(7);
                        if (method.equals("GET")) {
                            if (packs.containsKey(packId)) {
                                byte[] res = packs.get(packId);
                                exchange.sendResponseHeaders(200, res.length);
                                try (OutputStream os = exchange.getResponseBody()) {
                                    os.write(res);
                                }
                            } else {
                                exchange.sendResponseHeaders(404, -1);
                            }
                        } else if (method.equals("PUT")) {
                            byte[] body = exchange.getRequestBody().readAllBytes();
                            packs.put(packId, body);
                            exchange.sendResponseHeaders(200, -1);
                        }
                    } else if (path.equals("/pack.index")) {
                        if (method.equals("GET")) {
                            if (packIndex == null) {
                                exchange.sendResponseHeaders(404, -1);
                            } else {
                                byte[] res = packIndex.getBytes(StandardCharsets.UTF_8);
                                exchange.sendResponseHeaders(200, res.length);
                                try (OutputStream os = exchange.getResponseBody()) {
                                    os.write(res);
                                }
                            }
                        } else if (method.equals("PUT")) {
                            byte[] body = exchange.getRequestBody().readAllBytes();
                            packIndex = new String(body, StandardCharsets.UTF_8);
                            exchange.sendResponseHeaders(200, -1);
                        }
                    } else {
                        exchange.sendResponseHeaders(400, -1);
                    }
                } finally {
                    exchange.close();
                }
            }
        });
        
        server.start();
    }

    @AfterAll
    public static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @BeforeEach
    public void resetData() {
        refs.clear();
        packs.clear();
        packIndex = "";
        refsGetAttempts = 0;
        refsGetStatusToReturn = 200;
    }

    @Test
    public void testHttpRemoteClientSuccess() throws Exception {
        String remoteUrl = "http://localhost:" + port;
        RemoteClient client = new RemoteClient(remoteUrl);

        // Test refs
        assertNull(client.getRef("main"));
        client.putRef("main", "hash123");
        assertEquals("hash123", client.getRef("main"));

        // Test pack index
        Map<String, String> indexMap = client.downloadIndex();
        assertTrue(indexMap.isEmpty());

        Map<String, String> upMap = Map.of("obj1", "pack1", "obj2", "pack1");
        client.uploadIndex(upMap);
        Map<String, String> downMap = client.downloadIndex();
        assertEquals(2, downMap.size());
        assertEquals("pack1", downMap.get("obj1"));
        assertEquals("pack1", downMap.get("obj2"));

        // Test packs
        byte[] packData = "dummy-pack-data".getBytes(StandardCharsets.UTF_8);
        client.uploadPack("pack1", packData);
        byte[] downPack = client.downloadPack("pack1");
        assertArrayEquals(packData, downPack);
    }

    @Test
    public void testHttpRemoteClientErrorsAndRetries() throws Exception {
        String remoteUrl = "http://localhost:" + port;
        RemoteClient client = new RemoteClient(remoteUrl);

        // 1. Test 500 status code response during GET ref (should fail after retries)
        refsGetStatusToReturn = 500;
        assertThrows(IOException.class, () -> client.getRef("main"));
        assertTrue(refsGetAttempts > 1, "Should have attempted retries");

        // 2. Test downloadIndex 404 response
        packIndex = null;
        Map<String, String> indexMap = client.downloadIndex();
        assertTrue(indexMap.isEmpty(), "Should return empty map on 404");

        // 3. Test downloadPack non-existent pack
        assertThrows(IOException.class, () -> client.downloadPack("nonexistent"));
    }
}
