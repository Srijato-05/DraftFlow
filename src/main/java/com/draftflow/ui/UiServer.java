package com.draftflow.ui;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.draftflow.core.*;
import com.draftflow.db.FileMetadata;
import com.draftflow.db.MetadataStore;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class UiServer {

    private final CAS cas;
    private final MetadataStore db;
    private HttpServer server;
    private int port;

    public UiServer(CAS cas, MetadataStore db, int port) {
        this.cas = cas;
        this.db = db;
        this.port = port;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new IndexHandler());
        server.createContext("/api/dag", new DagHandler());
        server.createContext("/api/status", new StatusHandler());
        server.setExecutor(null); // default executor
        server.start();
        this.port = server.getAddress().getPort();
        System.out.println("DraftFlow UI Server running at: http://localhost:" + this.port);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    public int getPort() {
        return port;
    }

    // --- HANDLERS ---

    private class IndexHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            byte[] response = getDashboardHtml();
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }
    }

    private class DagHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String json = buildDagJson();
                byte[] response = json.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            } catch (Exception e) {
                e.printStackTrace();
                byte[] response = ("{\"error\": \"" + e.getMessage() + "\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(500, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            }
        }
    }

    private class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String json = buildStatusJson();
                byte[] response = json.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            } catch (Exception e) {
                byte[] response = ("{\"error\": \"" + e.getMessage() + "\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(500, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            }
        }
    }

    // --- JSON BUILDERS ---

    private String buildDagJson() throws IOException {
        // Find all reachable revisions
        Map<String, List<String>> refMap = new HashMap<>();
        for (String refName : db.getRefNames()) {
            String target = db.getRef(refName);
            if (target != null) {
                refMap.computeIfAbsent(target, k -> new ArrayList<>()).add(refName);
            }
        }

        Queue<String> queue = new LinkedList<>();
        for (List<String> refs : refMap.values()) {
            for (String r : refs) {
                String hash = db.getRef(r);
                if (hash != null) {
                    queue.add(hash);
                }
            }
        }
        
        String activeRev = db.getConfig("activeRevisionHash");
        if (activeRev != null) {
            queue.add(activeRev);
        }

        Set<String> visited = new HashSet<>();
        List<String> revisionsJsonList = new ArrayList<>();

        while (!queue.isEmpty()) {
            String curr = queue.poll();
            if (curr == null || visited.contains(curr)) {
                continue;
            }
            visited.add(curr);

            try {
                Revision rev = (Revision) cas.readObject(curr);
                StringBuilder parentsJson = new StringBuilder("[");
                for (int i = 0; i < rev.getParentHashes().size(); i++) {
                    parentsJson.append("\"").append(rev.getParentHashes().get(i)).append("\"");
                    if (i < rev.getParentHashes().size() - 1) {
                        parentsJson.append(",");
                    }
                    queue.add(rev.getParentHashes().get(i));
                }
                parentsJson.append("]");

                StringBuilder refsJson = new StringBuilder("[");
                List<String> refs = refMap.get(curr);
                if (refs != null) {
                    for (int i = 0; i < refs.size(); i++) {
                        refsJson.append("\"").append(refs.get(i)).append("\"");
                        if (i < refs.size() - 1) {
                            refsJson.append(",");
                        }
                    }
                }
                refsJson.append("]");

                String revJson = String.format(
                        "{\"hash\":\"%s\",\"treeHash\":\"%s\",\"parents\":%s,\"changeId\":\"%s\"," +
                                "\"author\":\"%s\",\"timestamp\":%d,\"message\":\"%s\",\"isDraft\":%b,\"refs\":%s}",
                        curr,
                        rev.getTreeHash(),
                        parentsJson.toString(),
                        rev.getChangeId(),
                        rev.getAuthor(),
                        rev.getTimestamp(),
                        rev.getMessage().replace("\"", "\\\""),
                        rev.isDraft(),
                        refsJson.toString()
                );
                revisionsJsonList.add(revJson);
            } catch (Exception ignored) {
                // Skip invalid or unreadable revision objects
            }
        }

        return "[" + String.join(",", revisionsJsonList) + "]";
    }

    private String buildStatusJson() throws IOException {
        String activeHead = db.getConfig("activeHead");
        String activeRev = db.getConfig("activeRevisionHash");
        String activeChange = db.getConfig("activeChangeId");

        List<FileMetadata> tracked = db.getAllFiles();
        List<String> modified = new ArrayList<>();
        List<String> deleted = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();

        for (FileMetadata file : tracked) {
            Path p = cas.getRootDir().resolve(file.getPath());
            if (!Files.exists(p)) {
                deleted.add(file.getPath());
            } else {
                if (file.getType().equals(ObjectType.CONFLICT.name())) {
                    conflicts.add(file.getPath());
                } else {
                    long size = Files.size(p);
                    long lastMod = Files.getLastModifiedTime(p).toMillis();
                    if (size != file.getSize() || lastMod != file.getLastModified()) {
                        modified.add(file.getPath());
                    }
                }
            }
        }

        return String.format(
                "{\"activeHead\":\"%s\",\"activeRevision\":\"%s\",\"activeChangeId\":\"%s\"," +
                        "\"modified\":%s,\"deleted\":%s,\"conflicts\":%s}",
                activeHead != null ? activeHead.replace("heads/", "") : "detached",
                activeRev != null ? activeRev : "",
                activeChange != null ? activeChange : "",
                toJsonArray(modified),
                toJsonArray(deleted),
                toJsonArray(conflicts)
        );
    }

    private String toJsonArray(List<String> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            sb.append("\"").append(list.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
            if (i < list.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    // --- EMBEDDED DASHBOARD HTML ---

    private byte[] getDashboardHtml() {
        try {
            InputStream is = getClass().getResourceAsStream("/web/index.html");
            if (is != null) {
                try (is) {
                    return is.readAllBytes();
                }
            }
        } catch (Exception ignored) {
        }

        // Fallback Premium Midnight Gold dashboard HTML
        String html = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>DraftFlow Dashboard</title>
                    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;600;800&family=Outfit:wght@400;700&display=swap" rel="stylesheet">
                    <style>
                        :root {
                            --bg-dark: #090a0f;
                            --bg-surface: rgba(22, 24, 35, 0.7);
                            --gold: #d4af37;
                            --gold-glow: rgba(212, 175, 55, 0.4);
                            --text: #f0f2f5;
                            --text-muted: #8a8d9a;
                            --border: rgba(212, 175, 55, 0.2);
                        }
                        * {
                            box-sizing: border-box;
                            margin: 0;
                            padding: 0;
                        }
                        body {
                            background-color: var(--bg-dark);
                            color: var(--text);
                            font-family: 'Inter', sans-serif;
                            min-height: 100vh;
                            overflow-x: hidden;
                        }
                        header {
                            background: linear-gradient(180deg, rgba(212, 175, 55, 0.1), transparent);
                            border-bottom: 1px solid var(--border);
                            padding: 20px 40px;
                            display: flex;
                            justify-content: space-between;
                            align-items: center;
                            backdrop-filter: blur(10px);
                            position: sticky;
                            top: 0;
                            z-index: 100;
                        }
                        h1 {
                            font-family: 'Outfit', sans-serif;
                            font-weight: 800;
                            font-size: 28px;
                            letter-spacing: -0.5px;
                            background: linear-gradient(90deg, #ffe082, #d4af37);
                            -webkit-background-clip: text;
                            -webkit-text-fill-color: transparent;
                        }
                        .container {
                            max-width: 1400px;
                            margin: 40px auto;
                            padding: 0 20px;
                            display: grid;
                            grid-template-columns: 1fr 380px;
                            gap: 30px;
                        }
                        .panel {
                            background: var(--bg-surface);
                            border: 1px solid var(--border);
                            border-radius: 16px;
                            padding: 30px;
                            backdrop-filter: blur(15px);
                            box-shadow: 0 8px 32px 0 rgba(0, 0, 0, 0.5);
                        }
                        h2 {
                            font-family: 'Outfit', sans-serif;
                            font-size: 20px;
                            margin-bottom: 20px;
                            color: var(--gold);
                            border-bottom: 1px solid var(--border);
                            padding-bottom: 8px;
                        }
                        #graph-container {
                            height: 600px;
                            overflow: auto;
                            position: relative;
                            border: 1px solid rgba(255, 255, 255, 0.05);
                            border-radius: 8px;
                            background: rgba(0,0,0,0.2);
                        }
                        .status-item {
                            margin-bottom: 15px;
                        }
                        .status-label {
                            font-size: 12px;
                            color: var(--text-muted);
                            text-transform: uppercase;
                            letter-spacing: 1px;
                        }
                        .status-value {
                            font-size: 16px;
                            font-weight: 600;
                            color: #fff;
                        }
                        .file-list {
                            margin-top: 15px;
                            font-family: monospace;
                            font-size: 13px;
                            max-height: 200px;
                            overflow-y: auto;
                        }
                        .file-item {
                            padding: 6px 10px;
                            border-radius: 4px;
                            margin-bottom: 5px;
                        }
                        .modified { background: rgba(212, 175, 55, 0.1); color: var(--gold); }
                        .deleted { background: rgba(244, 67, 54, 0.1); color: #f44336; }
                        .conflict { background: rgba(255, 152, 0, 0.15); color: #ff9800; border: 1px dashed #ff9800; }
                    </style>
                </head>
                <body>
                    <header>
                        <h1>DRAFTFLOW</h1>
                        <span style="color: var(--gold); font-weight:600;">Core Dashboard</span>
                    </header>
                    <div class="container">
                        <div class="panel">
                            <h2>Revision History DAG</h2>
                            <div id="graph-container">
                                <svg id="dag-svg" width="100%" height="100%" style="min-height:500px;"></svg>
                            </div>
                        </div>
                        <div class="panel" style="display:flex; flex-direction:column; gap:25px;">
                            <div>
                                <h2>Working Copy Status</h2>
                                <div class="status-item">
                                    <div class="status-label">Active Branch</div>
                                    <div class="status-value" id="active-branch">-</div>
                                </div>
                                <div class="status-item">
                                    <div class="status-label">Active Revision</div>
                                    <div class="status-value" id="active-rev" style="font-family:monospace; font-size:14px;">-</div>
                                </div>
                            </div>
                            <div>
                                <h2>Modified Files</h2>
                                <div id="modified-list" class="file-list"></div>
                            </div>
                            <div>
                                <h2>Conflicts</h2>
                                <div id="conflict-list" class="file-list"></div>
                            </div>
                        </div>
                    </div>

                    <script>
                        async function loadStatus() {
                            const res = await fetch('/api/status');
                            const data = await res.json();
                            document.getElementById('active-branch').innerText = data.activeHead || 'detached';
                            document.getElementById('active-rev').innerText = data.activeRevision ? data.activeRevision.substring(0, 12) : 'None';

                            const modContainer = document.getElementById('modified-list');
                            modContainer.innerHTML = '';
                            data.modified.forEach(f => {
                                modContainer.innerHTML += `<div class="file-item modified">M  ${f}</div>`;
                            });
                            data.deleted.forEach(f => {
                                modContainer.innerHTML += `<div class="file-item deleted">D  ${f}</div>`;
                            });

                            const conflictContainer = document.getElementById('conflict-list');
                            conflictContainer.innerHTML = '';
                            data.conflicts.forEach(f => {
                                conflictContainer.innerHTML += `<div class="file-item conflict">C  ${f}</div>`;
                            });
                            if(data.conflicts.length === 0 && data.modified.length === 0 && data.deleted.length === 0) {
                                modContainer.innerHTML = '<span style="color:var(--text-muted)">Working copy clean.</span>';
                            }
                        }

                        async function loadDag() {
                            const res = await fetch('/api/dag');
                            const nodes = await res.json();
                            const svg = document.getElementById('dag-svg');
                            svg.innerHTML = '';

                            if(nodes.length === 0) return;

                            // Sort by timestamp
                            nodes.sort((a,b) => b.timestamp - a.timestamp);

                            const nodeHeight = 50;
                            const spacingY = 70;
                            const startX = 150;
                            
                            // Map nodes to coordinates
                            const nodeMap = {};
                            nodes.forEach((n, idx) => {
                                nodeMap[n.hash] = {
                                    x: startX + (n.isDraft ? 80 : 0),
                                    y: 50 + idx * spacingY,
                                    data: n
                                };
                            });

                            // Draw lines
                            nodes.forEach(n => {
                                const from = nodeMap[n.hash];
                                n.parents.forEach(pHash => {
                                    const to = nodeMap[pHash];
                                    if(to) {
                                        const line = document.createElementNS("http://www.w3.org/2000/svg", "line");
                                        line.setAttribute("x1", from.x);
                                        line.setAttribute("y1", from.y);
                                        line.setAttribute("x2", to.x);
                                        line.setAttribute("y2", to.y);
                                        line.setAttribute("stroke", "rgba(212,175,55,0.4)");
                                        line.setAttribute("stroke-width", "2");
                                        if(from.data.isDraft) {
                                            line.setAttribute("stroke-dasharray", "4");
                                        }
                                        svg.appendChild(line);
                                    }
                                });
                            });

                            // Draw circles
                            nodes.forEach(n => {
                                const coord = nodeMap[n.hash];
                                const group = document.createElementNS("http://www.w3.org/2000/svg", "g");

                                const circle = document.createElementNS("http://www.w3.org/2000/svg", "circle");
                                circle.setAttribute("cx", coord.x);
                                circle.setAttribute("cy", coord.y);
                                circle.setAttribute("r", n.isDraft ? "12" : "15");
                                circle.setAttribute("fill", n.isDraft ? "transparent" : "#d4af37");
                                circle.setAttribute("stroke", "#d4af37");
                                circle.setAttribute("stroke-width", "3");
                                if(n.isDraft) {
                                    circle.setAttribute("stroke-dasharray", "3");
                                }
                                group.appendChild(circle);

                                // Add label
                                const text = document.createElementNS("http://www.w3.org/2000/svg", "text");
                                text.setAttribute("x", coord.x + 25);
                                text.setAttribute("y", coord.y + 5);
                                text.setAttribute("fill", "#fff");
                                text.setAttribute("font-size", "12px");
                                text.setAttribute("font-family", "monospace");
                                
                                let refLabel = "";
                                if(n.refs.length > 0) {
                                    refLabel = ` [${n.refs.map(r => r.replace('heads/', '')).join(', ')}]`;
                                }

                                text.textContent = `${n.hash.substring(0,8)} - ${n.message}${refLabel}`;
                                group.appendChild(text);

                                svg.appendChild(group);
                            });

                            svg.setAttribute("height", (nodes.length * spacingY + 100) + "px");
                        }

                        loadStatus();
                        loadDag();
                        setInterval(loadStatus, 2000);
                        setInterval(loadDag, 5000);
                    </script>
                </body>
                </html>
                """;
        return html.getBytes(StandardCharsets.UTF_8);
    }
}
