/**
 * @file RemoteClient.java
 * @description Sync gateway and client connection manager for DraftFlow VCS.
 * Communicates with remote repositories, handling object pack transmissions (`.dfpack`) and
 * remote references.
 * 
 * DESIGN RATIONALE:
 * - Supports dual synchronization models: local mock directories (using the `file://` protocol)
 *   and REST servers (using HTTP GET/PUT requests).
 * - Incorporates robust transient connection handling via exponential backoff retries (up to 3 times).
 * - Minimizes transfer overhead by packing objects into single pack files (`.dfpack`) indexed
 *   via a central `pack.index` manifest.
 * - Writes to local/remote files using UUID temp files and atomic renames to prevent partial sync corruptions.
 */

package com.draftflow.remote;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class RemoteClient {

    private final String remoteUrl;
    private final HttpClient httpClient;

    public RemoteClient(String remoteUrl) {
        String normalized = normalizeUrl(remoteUrl);
        this.remoteUrl = normalized.endsWith("/") ? normalized : normalized + "/";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();
    }

    private static String normalizeUrl(String url) {
        if (url == null) return "";
        url = url.trim();
        if (url.startsWith("git@")) {
            // git@host:path/to/repo.git -> http://host/path/to/repo/
            String content = url.substring(4).replace(":", "/");
            if (content.endsWith(".git")) {
                content = content.substring(0, content.length() - 4);
            }
            String mapped = "http://" + content;
            System.out.println("[RemoteClient] Mapped SSH Git URL '" + url + "' to DraftFlow HTTP remote protocol: " + mapped);
            return mapped;
        } else if (url.startsWith("ssh://")) {
            // ssh://user@host/path/to/repo.git -> http://host/path/to/repo/
            String content = url.substring(6);
            if (content.contains("@")) {
                content = content.substring(content.indexOf("@") + 1);
            }
            if (content.endsWith(".git")) {
                content = content.substring(0, content.length() - 4);
            }
            String mapped = "http://" + content;
            System.out.println("[RemoteClient] Mapped SSH Remote URL '" + url + "' to DraftFlow HTTP remote protocol: " + mapped);
            return mapped;
        }
        return url;
    }

    @FunctionalInterface
    private interface NetworkCall<T> {
        T execute() throws IOException, InterruptedException;
    }

    private <T> T executeWithRetry(NetworkCall<T> call) throws IOException, InterruptedException {
        int maxRetries = 3;
        int delay = 100; // ms
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return call.execute();
            } catch (IOException e) {
                if (attempt == maxRetries) {
                    throw e;
                }
                Thread.sleep(delay);
                delay *= 2; // Exponential backoff
            }
        }
        throw new IOException("Unexpected exhaustion of retries");
    }

    public String getRef(String refName) throws IOException, InterruptedException {
        if (remoteUrl.startsWith("file://")) {
            Path refPath = getLocalPath("refs/" + refName);
            if (!Files.exists(refPath)) {
                return null;
            }
            return Files.readString(refPath).trim();
        } else {
            return executeWithRetry(() -> {
                URI uri = URI.create(remoteUrl + "refs/" + refName);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(uri)
                        .timeout(java.time.Duration.ofSeconds(10))
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 404) {
                    return null;
                }
                if (response.statusCode() != 200) {
                    throw new IOException("Failed to get remote ref: HTTP " + response.statusCode());
                }
                return response.body().trim();
            });
        }
    }

    public void putRef(String refName, String revisionHash) throws IOException, InterruptedException {
        if (remoteUrl.startsWith("file://")) {
            Path refPath = getLocalPath("refs/" + refName);
            Files.createDirectories(refPath.getParent());
            Path tempPath = refPath.getParent().resolve(refPath.getFileName().toString() + ".tmp_" + java.util.UUID.randomUUID());
            try {
                Files.writeString(tempPath, revisionHash);
                Files.move(tempPath, refPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.deleteIfExists(tempPath);
                throw e;
            }
        } else {
            // Sign the ref update if ECDSA key pair exists
            String signature = null;
            String publicKey = null;
            try {
                Path privPath = Paths.get(".draftflow/id_ecdsa");
                Path pubPath = Paths.get(".draftflow/id_ecdsa.pub");
                if (Files.exists(privPath) && Files.exists(pubPath)) {
                    String privKeyStr = Files.readString(privPath, StandardCharsets.UTF_8).trim();
                    publicKey = Files.readString(pubPath, StandardCharsets.UTF_8).trim();
                    String payload = refName + ":" + revisionHash;
                    signature = com.draftflow.core.SignatureHelper.sign(payload.getBytes(StandardCharsets.UTF_8), privKeyStr);
                }
            } catch (Exception e) {
                System.err.println("Warning: Failed to cryptographically sign ref update: " + e.getMessage());
            }

            final String finalSig = signature;
            final String finalPub = publicKey;

            executeWithRetry(() -> {
                URI uri = URI.create(remoteUrl + "refs/" + refName);
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(uri)
                        .timeout(java.time.Duration.ofSeconds(10))
                        .PUT(HttpRequest.BodyPublishers.ofString(revisionHash));
                if (finalSig != null && finalPub != null) {
                    builder.header("X-DF-Signature", finalSig);
                    builder.header("X-DF-PublicKey", finalPub);
                }
                HttpRequest request = builder.build();
                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IOException("Failed to update remote ref: HTTP " + response.statusCode());
                }
                return null;
            });
        }
    }

    public void uploadPack(String packId, byte[] packData) throws IOException, InterruptedException {
        if (remoteUrl.startsWith("file://")) {
            Path packPath = getLocalPath("packs/" + packId + ".dfpack");
            Files.createDirectories(packPath.getParent());
            Path tempPath = packPath.getParent().resolve(packPath.getFileName().toString() + ".tmp_" + java.util.UUID.randomUUID());
            try {
                Files.write(tempPath, packData);
                Files.move(tempPath, packPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.deleteIfExists(tempPath);
                throw e;
            }
        } else {
            executeWithRetry(() -> {
                URI uri = URI.create(remoteUrl + "packs/" + packId + ".dfpack");
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(uri)
                        .timeout(java.time.Duration.ofSeconds(30))
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(packData))
                        .build();
                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IOException("Failed to upload pack: HTTP " + response.statusCode());
                }
                return null;
            });
        }
    }

    public byte[] downloadPack(String packId) throws IOException, InterruptedException {
        if (remoteUrl.startsWith("file://")) {
            Path packPath = getLocalPath("packs/" + packId + ".dfpack");
            if (!Files.exists(packPath)) {
                throw new IOException("Remote packfile not found: " + packId);
            }
            return Files.readAllBytes(packPath);
        } else {
            return executeWithRetry(() -> {
                URI uri = URI.create(remoteUrl + "packs/" + packId + ".dfpack");
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(uri)
                        .timeout(java.time.Duration.ofSeconds(30))
                        .GET()
                        .build();
                HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() != 200) {
                    throw new IOException("Failed to download remote pack: HTTP " + response.statusCode());
                }
                return response.body();
            });
        }
    }

    public void uploadIndex(Map<String, String> objectToPackMap) throws IOException, InterruptedException {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : objectToPackMap.entrySet()) {
            sb.append(entry.getKey()).append(" ").append(entry.getValue()).append("\n");
        }
        String content = sb.toString();

        if (remoteUrl.startsWith("file://")) {
            Path indexPath = getLocalPath("pack.index");
            Files.createDirectories(indexPath.getParent());
            Path tempPath = indexPath.getParent().resolve(indexPath.getFileName().toString() + ".tmp_" + java.util.UUID.randomUUID());
            try {
                Files.writeString(tempPath, content);
                Files.move(tempPath, indexPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.deleteIfExists(tempPath);
                throw e;
            }
        } else {
            executeWithRetry(() -> {
                URI uri = URI.create(remoteUrl + "pack.index");
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(uri)
                        .timeout(java.time.Duration.ofSeconds(15))
                        .PUT(HttpRequest.BodyPublishers.ofString(content))
                        .build();
                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IOException("Failed to upload index: HTTP " + response.statusCode());
                }
                return null;
            });
        }
    }

    public Map<String, String> downloadIndex() throws IOException, InterruptedException {
        Map<String, String> map = new HashMap<>();
        String content;

        if (remoteUrl.startsWith("file://")) {
            Path indexPath = getLocalPath("pack.index");
            if (!Files.exists(indexPath)) {
                return map;
            }
            content = Files.readString(indexPath);
        } else {
            content = executeWithRetry(() -> {
                URI uri = URI.create(remoteUrl + "pack.index");
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(uri)
                        .timeout(java.time.Duration.ofSeconds(15))
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 404) {
                    return "";
                }
                if (response.statusCode() != 200) {
                    throw new IOException("Failed to download index: HTTP " + response.statusCode());
                }
                return response.body();
            });
            if (content.isEmpty()) {
                return map;
            }
        }

        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split(" ");
            if (parts.length == 2) {
                map.put(parts[0], parts[1]);
            }
        }
        return map;
    }

    private Path getLocalPath(String relativePath) {
        String pathStr = remoteUrl.substring(7); // strip file://
        // Fix Windows URI path prefix (e.g. /C:/projects -> C:/projects)
        if (pathStr.startsWith("/") && pathStr.length() > 2 && pathStr.charAt(2) == ':') {
            pathStr = pathStr.substring(1);
        }
        return Paths.get(pathStr).resolve(relativePath).normalize().toAbsolutePath();
    }
}
