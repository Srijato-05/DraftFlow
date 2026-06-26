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
        server.createContext("/api/ledger", new LedgerHandler());
        server.createContext("/api/trace", new TraceHandler());
        server.createContext("/api/action", new ActionHandler());
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
                    <title>DraftFlow Premium Web GUI</title>
                    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&family=Outfit:wght@400;600;800&family=JetBrains+Mono:wght@400;700&display=swap" rel="stylesheet">
                    <style>
                        :root {
                            --bg-dark: #07080b;
                            --bg-card: rgba(18, 20, 29, 0.65);
                            --gold-gradient: linear-gradient(135deg, #f3d078 0%, #c39c38 100%);
                            --gold: #d4af37;
                            --gold-glow: rgba(212, 175, 55, 0.25);
                            --text-main: #f3f4f6;
                            --text-muted: #9ca3af;
                            --border-glow: rgba(212, 175, 55, 0.15);
                            --border-dim: rgba(255, 255, 255, 0.05);
                        }
                        * {
                            box-sizing: border-box;
                            margin: 0;
                            padding: 0;
                        }
                        body {
                            background-color: var(--bg-dark);
                            color: var(--text-main);
                            font-family: 'Inter', sans-serif;
                            min-height: 100vh;
                            display: flex;
                            flex-direction: column;
                            overflow-x: hidden;
                            background-image: 
                                radial-gradient(at 10% 20%, rgba(212, 175, 55, 0.04) 0px, transparent 50%),
                                radial-gradient(at 90% 80%, rgba(212, 175, 55, 0.03) 0px, transparent 50%);
                        }
                        header {
                            background: rgba(7, 8, 11, 0.8);
                            backdrop-filter: blur(16px);
                            -webkit-backdrop-filter: blur(16px);
                            border-bottom: 1px solid var(--border-dim);
                            padding: 18px 40px;
                            display: flex;
                            justify-content: space-between;
                            align-items: center;
                            position: sticky;
                            top: 0;
                            z-index: 100;
                        }
                        .logo-container {
                            display: flex;
                            align-items: center;
                            gap: 12px;
                        }
                        .logo-icon {
                            width: 14px;
                            height: 14px;
                            background: var(--gold-gradient);
                            border-radius: 50%;
                            box-shadow: 0 0 12px var(--gold);
                        }
                        h1 {
                            font-family: 'Outfit', sans-serif;
                            font-weight: 800;
                            font-size: 24px;
                            letter-spacing: -0.5px;
                            background: var(--gold-gradient);
                            -webkit-background-clip: text;
                            -webkit-text-fill-color: transparent;
                        }
                        .tabs {
                            display: flex;
                            gap: 10px;
                        }
                        .tab-btn {
                            background: transparent;
                            border: 1px solid transparent;
                            color: var(--text-muted);
                            padding: 8px 16px;
                            border-radius: 8px;
                            font-size: 13px;
                            font-weight: 500;
                            cursor: pointer;
                            transition: all 0.2s ease;
                        }
                        .tab-btn:hover {
                            color: #fff;
                            background: rgba(255,255,255,0.03);
                        }
                        .tab-btn.active {
                            color: var(--gold);
                            background: rgba(212, 175, 55, 0.1);
                            border-color: var(--border-glow);
                        }
                        .container {
                            max-width: 1500px;
                            width: 100%;
                            margin: 30px auto;
                            padding: 0 30px;
                            flex-grow: 1;
                        }
                        .tab-content {
                            display: none;
                            animation: fadeIn 0.3s ease;
                        }
                        .tab-content.active {
                            display: grid;
                            grid-template-columns: 1fr 420px;
                            gap: 30px;
                        }
                        @keyframes fadeIn {
                            from { opacity: 0; transform: translateY(5px); }
                            to { opacity: 1; transform: translateY(0); }
                        }
                        .panel {
                            background: var(--bg-card);
                            border: 1px solid var(--border-dim);
                            border-radius: 20px;
                            padding: 30px;
                            backdrop-filter: blur(20px);
                            -webkit-backdrop-filter: blur(20px);
                            box-shadow: 0 10px 40px rgba(0, 0, 0, 0.6);
                            position: relative;
                            transition: border-color 0.3s ease;
                        }
                        .panel:hover {
                            border-color: rgba(212, 175, 55, 0.15);
                        }
                        h2 {
                            font-family: 'Outfit', sans-serif;
                            font-size: 18px;
                            font-weight: 600;
                            letter-spacing: -0.2px;
                            margin-bottom: 20px;
                            color: var(--gold);
                            display: flex;
                            align-items: center;
                            gap: 8px;
                            border-bottom: 1px solid var(--border-dim);
                            padding-bottom: 12px;
                        }
                        #graph-container {
                            height: 620px;
                            overflow: auto;
                            border: 1px solid var(--border-dim);
                            border-radius: 14px;
                            background: rgba(3, 4, 6, 0.4);
                            position: relative;
                        }
                        /* Scrollbar */
                        ::-webkit-scrollbar {
                            width: 8px;
                            height: 8px;
                        }
                        ::-webkit-scrollbar-track {
                            background: transparent;
                        }
                        ::-webkit-scrollbar-thumb {
                            background: rgba(255, 255, 255, 0.1);
                            border-radius: 4px;
                        }
                        ::-webkit-scrollbar-thumb:hover {
                            background: rgba(212, 175, 55, 0.3);
                        }
                        .status-card {
                            background: rgba(255, 255, 255, 0.02);
                            border: 1px solid var(--border-dim);
                            border-radius: 12px;
                            padding: 20px;
                            margin-bottom: 20px;
                        }
                        .status-grid {
                            display: grid;
                            grid-template-columns: 1fr;
                            gap: 15px;
                        }
                        .status-item {
                            display: flex;
                            flex-direction: column;
                            gap: 4px;
                        }
                        .status-label {
                            font-size: 11px;
                            font-weight: 500;
                            color: var(--text-muted);
                            text-transform: uppercase;
                            letter-spacing: 0.8px;
                        }
                        .status-value {
                            font-size: 14px;
                            font-weight: 600;
                            color: #ffffff;
                            word-break: break-all;
                        }
                        .file-list {
                            display: flex;
                            flex-direction: column;
                            gap: 8px;
                            max-height: 220px;
                            overflow-y: auto;
                            padding-right: 4px;
                        }
                        .file-item {
                            padding: 10px 14px;
                            border-radius: 8px;
                            font-family: 'JetBrains Mono', monospace;
                            font-size: 12px;
                            display: flex;
                            align-items: center;
                            justify-content: space-between;
                            border: 1px solid transparent;
                            transition: all 0.2s ease;
                        }
                        .file-item:hover {
                            transform: translateX(4px);
                        }
                        .modified { 
                            background: rgba(212, 175, 55, 0.06); 
                            color: #f3d078;
                            border-color: rgba(212, 175, 55, 0.12);
                        }
                        .deleted { 
                            background: rgba(239, 68, 68, 0.06); 
                            color: #f87171;
                            border-color: rgba(239, 68, 68, 0.12);
                        }
                        .conflict { 
                            background: rgba(249, 115, 22, 0.08); 
                            color: #fb923c;
                            border-color: rgba(249, 115, 22, 0.2);
                            animation: pulse-orange 2s infinite;
                        }
                        @keyframes pulse-orange {
                            0% { box-shadow: 0 0 0 0 rgba(249, 115, 22, 0.4); }
                            70% { box-shadow: 0 0 0 6px rgba(249, 115, 22, 0); }
                            100% { box-shadow: 0 0 0 0 rgba(249, 115, 22, 0); }
                        }
                        .btn {
                            background: var(--gold-gradient);
                            border: none;
                            color: #000;
                            padding: 10px 18px;
                            border-radius: 8px;
                            font-size: 13px;
                            font-weight: 700;
                            cursor: pointer;
                            transition: all 0.2s ease;
                            display: inline-flex;
                            align-items: center;
                            justify-content: center;
                            gap: 6px;
                        }
                        .btn:hover {
                            box-shadow: 0 0 15px rgba(212, 175, 55, 0.4);
                            transform: translateY(-1px);
                        }
                        .btn-sec {
                            background: rgba(255,255,255,0.05);
                            border: 1px solid var(--border-dim);
                            color: #fff;
                        }
                        .btn-sec:hover {
                            background: rgba(255,255,255,0.1);
                            box-shadow: none;
                        }
                        .btn-danger {
                            background: linear-gradient(135deg, #ef4444 0%, #b91c1c 100%);
                            color: #fff;
                        }
                        .btn-danger:hover {
                            box-shadow: 0 0 15px rgba(239, 68, 68, 0.4);
                        }
                        input[type="text"] {
                            background: rgba(255,255,255,0.03);
                            border: 1px solid var(--border-dim);
                            color: #fff;
                            padding: 10px 14px;
                            border-radius: 8px;
                            font-size: 13px;
                            width: 100%;
                            outline: none;
                            transition: border-color 0.2s ease;
                        }
                        input[type="text"]:focus {
                            border-color: var(--gold);
                        }
                        .input-group {
                            display: flex;
                            flex-direction: column;
                            gap: 8px;
                            margin-bottom: 15px;
                        }
                        .input-group label {
                            font-size: 11px;
                            font-weight: 600;
                            color: var(--text-muted);
                            text-transform: uppercase;
                            letter-spacing: 0.5px;
                        }
                        /* SVG Graph Styles */
                        .node-circle {
                            transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
                            cursor: pointer;
                        }
                        .node-circle:hover {
                            r: 16;
                            fill: #ffffff;
                            stroke-width: 5;
                            filter: drop-shadow(0px 0px 8px var(--gold));
                        }
                        .edge-path {
                            stroke-dasharray: 1000;
                            stroke-dashoffset: 1000;
                            animation: draw 1.5s ease forwards;
                        }
                        @keyframes draw {
                            to { stroke-dashoffset: 0; }
                        }
                        /* Modal Info Panel */
                        .detail-panel {
                            background: rgba(13, 15, 24, 0.95);
                            border: 1px solid var(--border-glow);
                            box-shadow: 0 20px 50px rgba(0,0,0,0.8);
                            border-radius: 16px;
                            padding: 24px;
                            display: none;
                            position: absolute;
                            bottom: 20px;
                            left: 20px;
                            right: 20px;
                            z-index: 10;
                            backdrop-filter: blur(25px);
                            animation: slideUp 0.3s cubic-bezier(0.16, 1, 0.3, 1);
                        }
                        @keyframes slideUp {
                            from { transform: translateY(20px); opacity: 0; }
                            to { transform: translateY(0); opacity: 1; }
                        }
                        .detail-close {
                            position: absolute;
                            top: 15px;
                            right: 15px;
                            cursor: pointer;
                            color: var(--text-muted);
                            border: none;
                            background: transparent;
                            font-size: 18px;
                        }
                        .detail-close:hover {
                            color: #fff;
                        }
                        .ref-tag {
                            background: var(--gold-gradient);
                            color: #000;
                            padding: 2px 8px;
                            border-radius: 4px;
                            font-size: 11px;
                            font-weight: 700;
                            margin-right: 5px;
                            display: inline-block;
                        }
                        /* Toast System */
                        #toast {
                            position: fixed;
                            bottom: 30px;
                            right: 30px;
                            background: rgba(18, 20, 29, 0.95);
                            border: 1px solid var(--border-glow);
                            box-shadow: 0 10px 30px rgba(0,0,0,0.5);
                            border-radius: 10px;
                            padding: 16px 24px;
                            color: #fff;
                            font-size: 13px;
                            display: none;
                            z-index: 999;
                            align-items: center;
                            gap: 10px;
                            backdrop-filter: blur(10px);
                            animation: slideIn 0.3s ease;
                        }
                        @keyframes slideIn {
                            from { transform: translateX(50px); opacity: 0; }
                            to { transform: translateX(0); opacity: 1; }
                        }
                        /* Trace Table */
                        .trace-table {
                            width: 100%;
                            border-collapse: collapse;
                            font-size: 12px;
                            margin-top: 15px;
                        }
                        .trace-table th, .trace-table td {
                            padding: 10px;
                            border-bottom: 1px solid var(--border-dim);
                            text-align: left;
                        }
                        .trace-table th {
                            color: var(--gold);
                            font-weight: 600;
                        }
                        .trace-table td.code-cell {
                            font-family: 'JetBrains Mono', monospace;
                            color: #fff;
                        }
                        /* Ledger list */
                        .ledger-list {
                            display: flex;
                            flex-direction: column;
                            gap: 10px;
                        }
                        .ledger-item {
                            padding: 12px 16px;
                            background: rgba(255,255,255,0.02);
                            border: 1px solid var(--border-dim);
                            border-radius: 8px;
                            font-family: 'JetBrains Mono', monospace;
                            font-size: 12px;
                            display: flex;
                            justify-content: space-between;
                        }
                    </style>
                </head>
                <body>
                    <header>
                        <div class="logo-container">
                            <div class="logo-icon"></div>
                            <h1>DRAFTFLOW</h1>
                        </div>
                        <div class="tabs">
                            <button class="tab-btn active" onclick="switchTab('dag')">DAG History</button>
                            <button class="tab-btn" onclick="switchTab('workspace')">Workspace Tools</button>
                            <button class="tab-btn" onclick="switchTab('trace')">Trace Explorer</button>
                            <button class="tab-btn" onclick="switchTab('ledger')">Ledger Log</button>
                        </div>
                        <div class="status-badge">Independent VCS</div>
                    </header>
                    <div class="container">
                        <!-- TAB: DAG -->
                        <div id="tab-dag" class="tab-content active">
                            <div class="panel">
                                <h2>Revision History DAG</h2>
                                <div id="graph-container">
                                    <svg id="dag-svg" width="100%" height="100%" style="min-height:550px;"></svg>
                                    
                                    <div id="detail-panel" class="detail-panel">
                                        <button class="detail-close" onclick="closeDetails()">&times;</button>
                                        <h3 id="det-msg" style="color:var(--gold); font-family:'Outfit'; margin-bottom:10px;">Select a Node</h3>
                                        <div style="display:grid; grid-template-columns: 1fr 1fr; gap:15px; font-size:13px; font-family:'Inter'; margin-bottom: 20px;">
                                            <div>
                                                <span style="color:var(--text-muted);">Revision Hash:</span>
                                                <p id="det-hash" style="font-family:monospace; color:#fff;"></p>
                                            </div>
                                            <div>
                                                <span style="color:var(--text-muted);">Change ID:</span>
                                                <p id="det-change" style="font-family:monospace; color:#fff;"></p>
                                            </div>
                                            <div>
                                                <span style="color:var(--text-muted);">Author:</span>
                                                <p id="det-author" style="color:#fff;"></p>
                                            </div>
                                            <div>
                                                <span style="color:var(--text-muted);">Timestamp:</span>
                                                <p id="det-time" style="color:#fff;"></p>
                                            </div>
                                        </div>
                                        <div style="display:flex; gap:10px;">
                                            <button class="btn btn-sec" onclick="actionSwitchSelected()">Checkout Commit</button>
                                            <button class="btn" onclick="actionRebaseSelected()">Rebase onto this</button>
                                        </div>
                                    </div>
                                </div>
                            </div>
                            <div class="panel" style="display:flex; flex-direction:column; gap:25px;">
                                <div>
                                    <h2>Working Copy Status</h2>
                                    <div class="status-card">
                                        <div class="status-grid">
                                            <div class="status-item">
                                                <div class="status-label">Active Branch</div>
                                                <div class="status-value" id="active-branch" style="color:var(--gold);">-</div>
                                            </div>
                                            <div class="status-item">
                                                <div class="status-label">Active Revision</div>
                                                <div class="status-value" id="active-rev" style="font-family:monospace;">-</div>
                                            </div>
                                        </div>
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

                        <!-- TAB: WORKSPACE TOOLS -->
                        <div id="tab-workspace" class="tab-content">
                            <div class="panel" style="display:flex; flex-direction:column; gap:25px;">
                                <h2>Interactive VCS Actions</h2>
                                
                                <div class="input-group">
                                    <label>Save Changes (Commit)</label>
                                    <input type="text" id="commit-msg-input" placeholder="Enter commit message...">
                                    <div style="margin-top:8px;">
                                        <button class="btn" onclick="actionSave()">Save Commit</button>
                                    </div>
                                </div>

                                <div class="input-group">
                                    <label>Rebase Current Branch</label>
                                    <input type="text" id="rebase-upstream-input" placeholder="Enter upstream branch or commit hash...">
                                    <div style="margin-top:8px;">
                                        <button class="btn" onclick="actionRebaseInput()">Rebase Onto Target</button>
                                    </div>
                                </div>

                                <div style="display:flex; gap:12px; margin-top:15px; border-top:1px solid var(--border-dim); padding-top:20px;">
                                    <button class="btn btn-danger" onclick="actionClean()">Workspace Sweeper (Clean)</button>
                                    <button class="btn btn-sec" onclick="actionUndo()">Undo Last Commit</button>
                                </div>
                            </div>
                            <div class="panel">
                                <h2>Active Branch Meta</h2>
                                <div class="status-card" style="margin-bottom:0;">
                                    <div class="status-grid" style="gap:20px;">
                                        <div class="status-item">
                                            <div class="status-label">Quick Switch Branch</div>
                                            <input type="text" id="switch-branch-input" placeholder="e.g. main, feature-1" style="margin-bottom:8px;">
                                            <button class="btn btn-sec" onclick="actionSwitchInput()">Switch Branch</button>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <!-- TAB: TRACE EXPLORER -->
                        <div id="tab-trace" class="tab-content">
                            <div class="panel" style="grid-column: span 2;">
                                <h2>Trace Explorer</h2>
                                <p style="color:var(--text-muted); font-size:13px; margin-bottom:20px;">
                                    "Trace" analyzes a file line-by-line, determining the exact commit, author, and timestamp that last modified each line.
                                </p>
                                <div style="display:flex; gap:12px; margin-bottom:20px;">
                                    <input type="text" id="trace-file-input" placeholder="e.g. tracked.txt" style="max-width:350px;">
                                    <button class="btn" onclick="loadTrace()">Trace File</button>
                                </div>
                                <div style="overflow-x:auto;">
                                    <table class="trace-table" id="trace-table" style="display:none;">
                                        <thead>
                                            <tr>
                                                <th style="width:70px;">Line</th>
                                                <th style="width:100px;">Commit</th>
                                                <th style="width:150px;">Author</th>
                                                <th style="width:180px;">Date</th>
                                                <th>Content</th>
                                            </tr>
                                        </thead>
                                        <tbody id="trace-tbody"></tbody>
                                    </table>
                                    <div id="trace-placeholder" style="color:var(--text-muted); font-size:13px; padding:20px 0; text-align:center;">
                                        Enter a file path and click Trace File to begin analysis.
                                    </div>
                                </div>
                            </div>
                        </div>

                        <!-- TAB: LEDGER -->
                        <div id="tab-ledger" class="tab-content">
                            <div class="panel" style="grid-column: span 2;">
                                <h2>Reference Transaction Log (Ledger)</h2>
                                <p style="color:var(--text-muted); font-size:13px; margin-bottom:20px;">
                                    Chronological log of reference state changes (checkouts, commits, rebases, and undo operations).
                                </p>
                                <div class="ledger-list" id="ledger-container">
                                    <div style="color:var(--text-muted); font-size:13px; text-align:center; padding:20px 0;">No ledger entries logged.</div>
                                </div>
                            </div>
                        </div>
                    </div>

                    <div id="toast">
                        <span id="toast-icon">✨</span>
                        <span id="toast-text">Action completed successfully!</span>
                    </div>

                    <script>
                        let nodeCache = {};
                        let selectedHash = null;

                        function showToast(text, isError = false) {
                            const t = document.getElementById('toast');
                            const txt = document.getElementById('toast-text');
                            const icon = document.getElementById('toast-icon');
                            txt.innerText = text;
                            icon.innerText = isError ? '❌' : '✨';
                            t.style.display = 'flex';
                            setTimeout(() => {
                                t.style.display = 'none';
                            }, 4000);
                        }

                        function switchTab(tabId) {
                            document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
                            document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
                            
                            const btn = Array.from(document.querySelectorAll('.tab-btn')).find(b => b.innerText.toLowerCase().includes(tabId));
                            if(btn) btn.classList.add('active');
                            
                            const content = document.getElementById('tab-' + tabId);
                            if(content) {
                                content.classList.add('active');
                                if(content.style.display === 'none') content.style.display = 'grid';
                            }
                            if (tabId === 'ledger') {
                                loadLedger();
                            }
                        }

                        async function postAction(url) {
                            try {
                                const res = await fetch(url, { method: 'POST' });
                                const data = await res.json();
                                if(data.error) {
                                    showToast(data.error, true);
                                } else {
                                    showToast(data.message || 'Action executed successfully!');
                                    loadStatus();
                                    loadDag();
                                }
                            } catch(e) {
                                showToast(e.message || 'Error occurred', true);
                            }
                        }

                        function actionSave() {
                            const msg = document.getElementById('commit-msg-input').value;
                            postAction('/api/action?cmd=save&msg=' + encodeURIComponent(msg));
                            document.getElementById('commit-msg-input').value = '';
                        }

                        function actionRebaseInput() {
                            const target = document.getElementById('rebase-upstream-input').value;
                            postAction('/api/action?cmd=rebase&upstream=' + encodeURIComponent(target));
                            document.getElementById('rebase-upstream-input').value = '';
                        }

                        function actionSwitchInput() {
                            const branch = document.getElementById('switch-branch-input').value;
                            postAction('/api/action?cmd=switch&target=' + encodeURIComponent(branch));
                            document.getElementById('switch-branch-input').value = '';
                        }

                        function actionSwitchSelected() {
                            if(!selectedHash) return;
                            postAction('/api/action?cmd=switch&target=' + selectedHash);
                            closeDetails();
                        }

                        function actionRebaseSelected() {
                            if(!selectedHash) return;
                            postAction('/api/action?cmd=rebase&upstream=' + selectedHash);
                            closeDetails();
                        }

                        function actionClean() {
                            postAction('/api/action?cmd=clean');
                        }

                        function actionUndo() {
                            postAction('/api/action?cmd=undo');
                        }

                        async function loadTrace() {
                            const file = document.getElementById('trace-file-input').value;
                            if(!file) {
                                showToast('Please enter a file path', true);
                                return;
                            }
                            try {
                                const res = await fetch('/api/trace?file=' + encodeURIComponent(file));
                                const data = await res.json();
                                if (data.error) {
                                    showToast(data.error, true);
                                    return;
                                }
                                const tbl = document.getElementById('trace-table');
                                const placeholder = document.getElementById('trace-placeholder');
                                const tbody = document.getElementById('trace-tbody');
                                tbody.innerHTML = '';
                                
                                data.forEach((row, idx) => {
                                    tbody.innerHTML += `
                                        <tr>
                                            <td style="color:var(--text-muted);">${idx + 1}</td>
                                            <td style="font-family:monospace; color:var(--gold);">${row.hash}</td>
                                            <td>${row.author}</td>
                                            <td style="color:var(--text-muted);">${row.date}</td>
                                            <td class="code-cell">${escapeHtml(row.line)}</td>
                                        </tr>
                                    `;
                                });
                                placeholder.style.display = 'none';
                                tbl.style.display = 'table';
                            } catch(e) {
                                showToast(e.message || 'Error running trace', true);
                            }
                        }

                        function escapeHtml(str) {
                            return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#039;");
                        }

                        async function loadLedger() {
                            try {
                                const res = await fetch('/api/ledger');
                                const data = await res.json();
                                const container = document.getElementById('ledger-container');
                                container.innerHTML = '';
                                if(data.length === 0) {
                                    container.innerHTML = '<div style="color:var(--text-muted); font-size:13px; text-align:center; padding:20px 0;">No ledger entries logged.</div>';
                                    return;
                                }
                                data.reverse().forEach((e, idx) => {
                                    container.innerHTML += `
                                        <div class="ledger-item">
                                            <span style="color:var(--gold); font-weight:bold;">${e.newHash.substring(0,7)}</span>
                                            <span style="color:#fff;">${e.message}</span>
                                        </div>
                                    `;
                                });
                            } catch(e) {
                                showToast('Failed to load ledger', true);
                            }
                        }

                        async function loadStatus() {
                            const res = await fetch('/api/status');
                            const data = await res.json();
                            document.getElementById('active-branch').innerText = data.activeHead || 'detached';
                            document.getElementById('active-rev').innerText = data.activeRevision ? data.activeRevision.substring(0, 12) : 'None';

                            const modContainer = document.getElementById('modified-list');
                            modContainer.innerHTML = '';
                            data.modified.forEach(f => {
                                modContainer.innerHTML += `<div class="file-item modified"><span>M</span><span>${f}</span></div>`;
                            });
                            data.deleted.forEach(f => {
                                modContainer.innerHTML += `<div class="file-item deleted"><span>D</span><span>${f}</span></div>`;
                            });

                            const conflictContainer = document.getElementById('conflict-list');
                            conflictContainer.innerHTML = '';
                            data.conflicts.forEach(f => {
                                conflictContainer.innerHTML += `<div class="file-item conflict"><span>C</span><span>${f}</span></div>`;
                            });
                            if(data.conflicts.length === 0 && data.modified.length === 0 && data.deleted.length === 0) {
                                modContainer.innerHTML = '<span style="color:var(--text-muted); font-size:13px;">Working copy clean.</span>';
                            }
                        }

                        function showDetails(hash) {
                            selectedHash = hash;
                            const n = nodeCache[hash];
                            if(!n) return;
                            document.getElementById('det-msg').innerText = n.message;
                            document.getElementById('det-hash').innerText = n.hash;
                            document.getElementById('det-change').innerText = n.changeId || 'None';
                            document.getElementById('det-author').innerText = n.author;
                            document.getElementById('det-time').innerText = new Date(n.timestamp).toLocaleString();
                            document.getElementById('detail-panel').style.display = 'block';
                        }

                        function closeDetails() {
                            document.getElementById('detail-panel').style.display = 'none';
                            selectedHash = null;
                        }

                        async function loadDag() {
                            const res = await fetch('/api/dag');
                            const nodes = await res.json();
                            const svg = document.getElementById('dag-svg');
                            svg.innerHTML = '';
                            nodeCache = {};

                            if(nodes.length === 0) return;

                            nodes.sort((a,b) => b.timestamp - a.timestamp);

                            const spacingY = 80;
                            const startX = 200;
                            
                            const nodeMap = {};
                            nodes.forEach((n, idx) => {
                                nodeCache[n.hash] = n;
                                nodeMap[n.hash] = {
                                    x: startX + (n.isDraft ? 80 : 0),
                                    y: 60 + idx * spacingY,
                                    data: n
                                };
                            });

                            nodes.forEach(n => {
                                const from = nodeMap[n.hash];
                                n.parents.forEach(pHash => {
                                    const to = nodeMap[pHash];
                                    if(to) {
                                        const path = document.createElementNS("http://www.w3.org/2000/svg", "path");
                                        const midY = (from.y + to.y) / 2;
                                        const d = `M ${from.x} ${from.y} C ${from.x} ${midY}, ${to.x} ${midY}, ${to.x} ${to.y}`;
                                        path.setAttribute("d", d);
                                        path.setAttribute("class", "edge-path");
                                        path.setAttribute("stroke", "rgba(212,175,55,0.45)");
                                        path.setAttribute("stroke-width", "2.5");
                                        path.setAttribute("fill", "none");
                                        if(from.data.isDraft) {
                                            path.setAttribute("stroke-dasharray", "5,5");
                                        }
                                        svg.appendChild(path);
                                    }
                                });
                            });

                            nodes.forEach(n => {
                                const coord = nodeMap[n.hash];
                                const group = document.createElementNS("http://www.w3.org/2000/svg", "g");

                                const circle = document.createElementNS("http://www.w3.org/2000/svg", "circle");
                                circle.setAttribute("cx", coord.x);
                                circle.setAttribute("cy", coord.y);
                                circle.setAttribute("r", n.isDraft ? "10" : "13");
                                circle.setAttribute("fill", n.isDraft ? "transparent" : "#d4af37");
                                circle.setAttribute("stroke", "#d4af37");
                                circle.setAttribute("stroke-width", "3");
                                circle.setAttribute("class", "node-circle");
                                if(n.isDraft) {
                                    circle.setAttribute("stroke-dasharray", "3");
                                }
                                circle.onclick = () => showDetails(n.hash);
                                group.appendChild(circle);

                                const text = document.createElementNS("http://www.w3.org/2000/svg", "text");
                                text.setAttribute("x", coord.x + 22);
                                text.setAttribute("y", coord.y + 4);
                                text.setAttribute("fill", "#ffffff");
                                text.setAttribute("font-size", "12px");
                                text.setAttribute("font-family", "monospace");
                                text.style.cursor = 'pointer';
                                text.onclick = () => showDetails(n.hash);
                                
                                let refLabel = "";
                                if(n.refs.length > 0) {
                                    refLabel = ` [${n.refs.map(r => r.replace('heads/', '')).join(', ')}]`;
                                }

                                text.textContent = `${n.hash.substring(0,8)} - ${n.message}${refLabel}`;
                                group.appendChild(text);

                                svg.appendChild(group);
                            });

                            svg.setAttribute("height", (nodes.length * spacingY + 120) + "px");
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

    private class LedgerHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                List<com.draftflow.core.ReflogManager.ReflogEntry> entries = com.draftflow.core.ReflogManager.getReflog(cas.getRootDir());
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < entries.size(); i++) {
                    com.draftflow.core.ReflogManager.ReflogEntry e = entries.get(i);
                    sb.append(String.format("{\"newHash\":\"%s\",\"message\":\"%s\"}",
                            e.getNewHash(),
                            e.getMessage().replace("\"", "\\\"").replace("\n", " ").replace("\r", " ")
                    ));
                    if (i < entries.size() - 1) sb.append(",");
                }
                sb.append("]");
                byte[] response = sb.toString().getBytes(StandardCharsets.UTF_8);
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

    private class TraceHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String query = exchange.getRequestURI().getQuery();
                String fileName = null;
                if (query != null) {
                    for (String pair : query.split("&")) {
                        String[] parts = pair.split("=", 2);
                        if (parts[0].equals("file") && parts.length > 1) {
                            fileName = java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                        }
                    }
                }
                if (fileName == null) {
                    throw new IllegalArgumentException("Missing file parameter");
                }
                Path fullPath = cas.getRootDir().resolve(fileName).toAbsolutePath().normalize();
                if (!Files.exists(fullPath)) {
                    throw new FileNotFoundException("File not found: " + fileName);
                }
                String relPath = cas.getRootDir().relativize(fullPath).toString().replace('\\', '/');

                String activeRev = db.getConfig("activeRevisionHash");
                if (activeRev == null) {
                    throw new IllegalStateException("No commits in this repository.");
                }

                List<String> currentLines = Files.readAllLines(fullPath, StandardCharsets.UTF_8);
                String[] finalTrace = new String[currentLines.size()];
                for (int i = 0; i < currentLines.size(); i++) {
                    finalTrace[i] = activeRev;
                }

                String currHash = activeRev;
                while (currHash != null) {
                    Revision rev = (Revision) cas.readObject(currHash);
                    if (rev.getParentHashes().isEmpty()) {
                        break;
                    }
                    String parentHash = rev.getParentHashes().get(0);
                    Revision parentRev = (Revision) cas.readObject(parentHash);

                    byte[] parentBytes = getFileContentAtCommit(parentRev.getTreeHash(), relPath);
                    if (parentBytes == null) {
                        break;
                    }
                    List<String> parentLines = Arrays.asList(new String(parentBytes, StandardCharsets.UTF_8).split("\\r?\\n"));

                    for (int i = 0; i < currentLines.size(); i++) {
                        if (finalTrace[i].equals(currHash)) {
                            if (parentLines.contains(currentLines.get(i))) {
                                finalTrace[i] = parentHash;
                            }
                        }
                    }

                    currHash = parentHash;
                }

                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < currentLines.size(); i++) {
                    String bh = finalTrace[i];
                    Revision r = (Revision) cas.readObject(bh);
                    String dateStr = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(r.getTimestamp()));
                    sb.append(String.format("{\"hash\":\"%s\",\"author\":\"%s\",\"date\":\"%s\",\"line\":\"%s\"}",
                            bh.substring(0, 8),
                            r.getAuthor().replace("\"", "\\\""),
                            dateStr,
                            currentLines.get(i).replace("\\", "\\\\").replace("\"", "\\\"")
                    ));
                    if (i < currentLines.size() - 1) sb.append(",");
                }
                sb.append("]");
                byte[] response = sb.toString().getBytes(StandardCharsets.UTF_8);
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

        private byte[] getFileContentAtCommit(String treeHash, String relPath) throws IOException {
            String[] parts = relPath.split("/");

            String currentTreeHash = treeHash;
            for (int i = 0; i < parts.length; i++) {
                Tree currentTree = (Tree) cas.readObject(currentTreeHash);
                String part = parts[i];
                TreeEntry entry = currentTree.getEntries().stream()
                        .filter(e -> e.getName().equals(part))
                        .findFirst().orElse(null);
                if (entry == null) {
                    return null;
                }
                if (i == parts.length - 1) {
                    if (entry.getType() == ObjectType.CHUNK_TREE) {
                        ChunkTree ct = (ChunkTree) cas.readObject(entry.getHash());
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        for (String ch : ct.getChunkHashes()) {
                            Blob b = (Blob) cas.readObject(ch);
                            out.write(b.getContent());
                        }
                        return out.toByteArray();
                    } else {
                        Blob b = (Blob) cas.readObject(entry.getHash());
                        return b.getContent();
                    }
                } else {
                    if (entry.getType() != ObjectType.TREE) {
                        return null;
                    }
                    currentTreeHash = entry.getHash();
                }
            }
            return null;
        }
    }

    private class ActionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            try {
                String query = exchange.getRequestURI().getQuery();
                Map<String, String> params = parseQuery(query);
                String cmd = params.get("cmd");
                if (cmd == null) {
                    throw new IllegalArgumentException("Missing cmd parameter");
                }

                String message = "Success";
                if (cmd.equals("clean")) {
                    com.draftflow.DraftFlow.CleanCmd clean = new com.draftflow.DraftFlow.CleanCmd();
                    java.lang.reflect.Field fForce = clean.getClass().getDeclaredField("force");
                    fForce.setAccessible(true);
                    fForce.set(clean, true);

                    java.lang.reflect.Field fDirs = clean.getClass().getDeclaredField("removeDirs");
                    fDirs.setAccessible(true);
                    fDirs.set(clean, true);

                    java.lang.reflect.Field fIgnored = clean.getClass().getDeclaredField("cleanIgnored");
                    fIgnored.setAccessible(true);
                    fIgnored.set(clean, true);

                    int res = clean.call();
                    if (res != 0) throw new RuntimeException("Clean returned code: " + res);
                    message = "Workspace successfully swept clean of all untracked files and directories!";
                } else if (cmd.equals("undo")) {
                    com.draftflow.DraftFlow.UndoCmd undo = new com.draftflow.DraftFlow.UndoCmd();
                    int res = undo.call();
                    if (res != 0) throw new RuntimeException("Undo returned code: " + res);
                    message = "Successfully undid last commit!";
                } else if (cmd.equals("switch")) {
                    String target = params.get("target");
                    if (target == null) throw new IllegalArgumentException("Missing target parameter");
                    com.draftflow.DraftFlow.SwitchCmd sw = new com.draftflow.DraftFlow.SwitchCmd();
                    java.lang.reflect.Field fRev = sw.getClass().getDeclaredField("revisionHash");
                    fRev.setAccessible(true);
                    fRev.set(sw, target);
                    int res = sw.call();
                    if (res != 0) throw new RuntimeException("Switch returned code: " + res);
                    message = "Successfully checked out " + target;
                } else if (cmd.equals("save")) {
                    String msg = params.get("msg");
                    if (msg == null || msg.trim().isEmpty()) {
                        msg = "Commit from Web GUI";
                    }
                    com.draftflow.DraftFlow.SaveCmd save = new com.draftflow.DraftFlow.SaveCmd();
                    java.lang.reflect.Field fMsg = save.getClass().getDeclaredField("message");
                    fMsg.setAccessible(true);
                    fMsg.set(save, msg);
                    int res = save.call();
                    if (res != 0) throw new RuntimeException("Save returned code: " + res);
                    message = "Changes successfully saved with message: " + msg;
                } else if (cmd.equals("rebase")) {
                    String upstream = params.get("upstream");
                    if (upstream == null) throw new IllegalArgumentException("Missing upstream parameter");
                    com.draftflow.DraftFlow.RebaseCmd rebase = new com.draftflow.DraftFlow.RebaseCmd();
                    java.lang.reflect.Field fUp = rebase.getClass().getDeclaredField("upstream");
                    fUp.setAccessible(true);
                    fUp.set(rebase, upstream);
                    int res = rebase.call();
                    if (res != 0) throw new RuntimeException("Rebase returned code: " + res);
                    message = "Successfully rebased current branch onto " + upstream;
                } else {
                    throw new IllegalArgumentException("Unknown command: " + cmd);
                }

                byte[] response = ("{\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
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

        private Map<String, String> parseQuery(String query) {
            Map<String, String> result = new HashMap<>();
            if (query == null) return result;
            for (String param : query.split("&")) {
                String[] pair = param.split("=", 2);
                if (pair.length > 1) {
                    try {
                        result.put(pair[0], java.net.URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
                    } catch (Exception ignored) {}
                } else {
                    result.put(pair[0], "");
                }
            }
            return result;
        }
    }
}

