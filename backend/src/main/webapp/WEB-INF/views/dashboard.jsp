<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.simulator.websocket.TVServerEndpoint" %>
<%@ page import="com.simulator.model.ClientState" %>
<%@ page import="com.simulator.dao.VisitHistoryDAO" %>
<%@ page import="com.simulator.dao.CommercialDAO" %>
<%@ page import="com.simulator.model.VisitRecord" %>
<%@ page import="com.simulator.model.Commercial" %>
<%@ page import="com.simulator.db.DBConnection" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.List" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
    <title>Broadcast Hub – Virtual TV Simulator</title>
    <link href="https://fonts.googleapis.com/css2?family=Orbitron:wght@400;700;900&family=Inter:wght@300;400;600&display=swap" rel="stylesheet"/>
    <style>
        *,*::before,*::after{box-sizing:border-box;margin:0;padding:0}
        :root{--primary:#00d4ff;--secondary:#7c3aed;--bg:#08080f;--surface:#12121a;--surface2:#1a1a28;--text:#e2e8f0;--muted:#64748b;--success:#22c55e;--danger:#ef4444;--warn:#f59e0b}
        body{background:var(--bg);color:var(--text);font-family:'Inter',sans-serif;min-height:100vh}
        header{background:var(--surface);border-bottom:1px solid #2d2d44;padding:18px 32px;display:flex;align-items:center;gap:16px}
        header h1{font-family:'Orbitron',monospace;font-size:1.4rem;background:linear-gradient(90deg,var(--primary),var(--secondary));-webkit-background-clip:text;-webkit-text-fill-color:transparent}
        .badge{background:rgba(0,212,255,0.15);border:1px solid var(--primary);color:var(--primary);padding:4px 12px;border-radius:20px;font-size:.75rem;font-family:'Orbitron',monospace}
        main{padding:32px;display:grid;gap:24px}
        .grid-2{display:grid;grid-template-columns:repeat(auto-fit,minmax(340px,1fr));gap:20px}
        .grid-3{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:20px}
        .card{background:var(--surface);border:1px solid #2d2d44;border-radius:12px;padding:24px}
        .card h2{font-family:'Orbitron',monospace;font-size:.95rem;color:var(--primary);margin-bottom:16px;text-transform:uppercase;letter-spacing:.08em}
        .stat-row{display:flex;justify-content:space-between;align-items:center;padding:10px 0;border-bottom:1px solid #2d2d4440;font-size:.9rem}
        .stat-row:last-child{border-bottom:none}
        .stat-label{color:var(--muted)}
        .stat-val{color:var(--primary);font-family:'Orbitron',monospace;font-weight:700}
        .btn{display:inline-flex;align-items:center;gap:8px;padding:10px 20px;border:none;border-radius:8px;cursor:pointer;font-size:.875rem;font-weight:600;text-decoration:none;transition:all .2s}
        .btn-primary{background:linear-gradient(135deg,var(--primary),var(--secondary));color:#fff}
        .btn-primary:hover{transform:translateY(-2px);box-shadow:0 6px 20px rgba(0,212,255,0.3)}
        .btn-outline{background:transparent;border:1px solid var(--primary);color:var(--primary)}
        .btn-outline:hover{background:rgba(0,212,255,.1)}
        .btn-danger{background:transparent;border:1px solid var(--danger);color:var(--danger);padding:6px 14px;font-size:.75rem}
        .btn-danger:hover{background:rgba(239,68,68,.1)}
        .client-table{width:100%;border-collapse:collapse;font-size:.875rem}
        .client-table th{color:var(--muted);text-transform:uppercase;font-size:.7rem;letter-spacing:.1em;padding:10px 12px;text-align:left;border-bottom:1px solid #2d2d44}
        .client-table td{padding:12px;border-bottom:1px solid #2d2d4420;vertical-align:middle}
        .client-table tr:hover td{background:rgba(255,255,255,.02)}
        .ch-badge{display:inline-block;background:rgba(0,212,255,.15);border:1px solid var(--primary);color:var(--primary);padding:2px 10px;border-radius:12px;font-family:'Orbitron',monospace;font-size:.75rem}
        .status-dot{width:9px;height:9px;border-radius:50%;display:inline-block}
        .status-dot.on{background:var(--success);box-shadow:0 0 6px var(--success)}
        .status-dot.off{background:var(--danger)}
        .channel-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(140px,1fr));gap:12px}
        .channel-card{background:var(--surface2);border:1px solid #2d2d44;border-radius:8px;padding:14px;cursor:pointer;transition:all .2s}
        .channel-card:hover{border-color:var(--primary);transform:translateY(-2px)}
        .channel-card .num{font-family:'Orbitron',monospace;font-size:1.2rem;font-weight:900;color:var(--primary)}
        .channel-card .name{font-size:.8rem;margin-top:4px;color:var(--text)}
        .channel-card .genre{font-size:.7rem;color:var(--muted);margin-top:2px}
        .actions{display:flex;gap:10px;flex-wrap:wrap;margin-top:16px}
        footer{text-align:center;padding:24px;color:var(--muted);font-size:.75rem;border-top:1px solid #2d2d44}
        .visit-bar{display:flex;align-items:center;gap:8px;padding:8px 0;border-bottom:1px solid #2d2d4420}
        .visit-bar:last-child{border-bottom:none}
        .visit-bar .ch-num{font-family:'Orbitron',monospace;color:var(--primary);font-weight:700;min-width:36px;text-align:center;font-size:.85rem}
        .visit-bar .ch-name{color:var(--text);font-size:.85rem;min-width:120px}
        .visit-bar .count{color:var(--secondary);font-family:'Orbitron',monospace;font-size:.85rem;font-weight:700}
        .visit-bar .bar-track{flex:1;height:6px;background:#2d2d44;border-radius:3px;overflow:hidden}
        .visit-bar .bar-fill{height:100%;background:linear-gradient(90deg,var(--primary),var(--secondary));border-radius:3px}
        .db-status{padding:4px 12px;border-radius:20px;font-size:.7rem;font-family:'Orbitron',monospace}
        .db-status.ok{background:rgba(34,197,94,.15);border:1px solid var(--success);color:var(--success)}
        .db-status.err{background:rgba(239,68,68,.15);border:1px solid var(--danger);color:var(--danger)}
        .commercial-item{background:var(--surface2);border:1px solid #2d2d44;border-radius:8px;padding:14px;margin-bottom:10px}
        .commercial-item .title{font-weight:600;color:var(--text);font-size:.9rem}
        .commercial-item .content{color:var(--muted);font-size:.8rem;margin-top:4px}
        .commercial-item .meta{font-size:.7rem;color:var(--muted);margin-top:6px;display:flex;gap:12px}
        .commercial-item .target{color:var(--primary);font-family:'Orbitron',monospace}
        .commercial-form{display:grid;gap:10px;margin-top:16px;padding-top:16px;border-top:1px solid #2d2d44}
        .commercial-form input,.commercial-form textarea,.commercial-form select{background:var(--surface2);border:1px solid #2d2d44;border-radius:6px;padding:8px 12px;color:var(--text);font-size:.85rem;font-family:'Inter',sans-serif}
        .commercial-form input:focus,.commercial-form textarea:focus,.commercial-form select:focus{outline:none;border-color:var(--primary)}
        .label{color:var(--muted);font-size:.75rem;margin-bottom:4px}
    </style>
</head>
<body>
<header>
    <h1>📡 Broadcast Hub</h1>
    <span class="badge">VIRTUAL TV SIMULATOR</span>
    <%
        boolean dbOk = DBConnection.isAvailable();
    %>
    <span class="db-status <%= dbOk ? "ok" : "err" %>"><%= dbOk ? "DB: Connected" : "DB: Offline" %></span>
    <div style="margin-left:auto;display:flex;gap:10px">
        <a href="<%= request.getContextPath() %>/client-player.jsp" class="btn btn-primary" target="_blank">📺 Open TV Client</a>
        <a href="<%= request.getContextPath() %>/api/status" class="btn btn-outline" target="_blank">🔌 API Status</a>
    </div>
</header>

<main>
    <!-- System Stats -->
    <div class="grid-2">
        <div class="card">
            <h2>📊 Server Overview</h2>
            <div class="stat-row">
                <span class="stat-label">Total Channels</span>
                <span class="stat-val"><%= TVServerEndpoint.getChannelCatalog().size() %></span>
            </div>
            <div class="stat-row">
                <span class="stat-label">Connected TVs</span>
                <span class="stat-val" id="connCount"><%= TVServerEndpoint.getTvSessions().size() %></span>
            </div>
            <div class="stat-row">
                <span class="stat-label">WebSocket Endpoint</span>
                <span class="stat-val" style="font-size:.7rem">ws://<%= request.getServerName() %>:<%= request.getServerPort() %><%= request.getContextPath() %>/ws/tv</span>
            </div>
            <div class="stat-row">
                <span class="stat-label">Server Time</span>
                <span class="stat-val" id="srvTime" style="font-size:.8rem"></span>
            </div>
            <div class="stat-row">
                <span class="stat-label">Database</span>
                <span class="stat-val"><%= dbOk ? "✅ MySQL Connected" : "⚠️ Offline" %></span>
            </div>
            <div class="actions">
                <a href="<%= request.getContextPath() %>/api/channels" class="btn btn-outline" target="_blank">View Channels JSON</a>
                <a href="<%= request.getContextPath() %>/api/clients"  class="btn btn-outline" target="_blank">View Clients JSON</a>
                <a href="<%= request.getContextPath() %>/api/visits/aggregate" class="btn btn-outline" target="_blank">Visit Stats JSON</a>
                <a href="<%= request.getContextPath() %>/api/commercials" class="btn btn-outline" target="_blank">Commercials JSON</a>
            </div>
        </div>

        <div class="card">
            <h2>📺 Connected TV Clients</h2>
            <%
                Map<String,ClientState> sessions = TVServerEndpoint.getTvSessions();
                if (sessions.isEmpty()) {
            %>
                <p style="color:var(--muted);font-size:.875rem">No TV clients connected yet. Open <a href="<%= request.getContextPath() %>/client-player.jsp" style="color:var(--primary)">client-player.jsp</a> in a new window.</p>
            <% } else { %>
                <table class="client-table">
                    <thead>
                        <tr><th>ID</th><th>Label</th><th>CH</th><th>Vol</th><th>State</th></tr>
                    </thead>
                    <tbody>
                    <% for (ClientState cs : sessions.values()) { %>
                        <tr>
                            <td><span class="ch-badge"><%= cs.getClientId() %></span></td>
                            <td><%= cs.getClientLabel() %></td>
                            <td><span class="ch-badge"><%= cs.getCurrentChannel() %></span></td>
                            <td><%= cs.getVolume() %>%</td>
                            <td>
                                <span class="status-dot <%= cs.isPowered() ? "on" : "off" %>"></span>
                                <%= cs.isPowered() ? "ON" : "OFF" %>
                                <%= cs.isMuted() ? "🔇" : "" %>
                            </td>
                        </tr>
                    <% } %>
                    </tbody>
                </table>
            <% } %>
        </div>
    </div>

    <!-- Visit History Recommendations -->
    <div class="card">
        <h2>📊 Channel Visit History & Recommendations</h2>
        <p style="color:var(--muted);font-size:.8rem;margin-bottom:16px">
            Aggregate visit counts across all users – channels with more visits are recommended more prominently.
        </p>
        <%
            if (dbOk) {
                VisitHistoryDAO visitDAO = new VisitHistoryDAO();
                List<VisitRecord> visits = visitDAO.getAggregateVisits();
                if (visits.isEmpty()) {
            %>
                <p style="color:var(--muted);font-size:.875rem">No visit data yet. Start watching channels to populate this!</p>
            <% } else {
                    int maxCount = visits.stream().mapToInt(VisitRecord::getVisitCount).max().orElse(1);
                    for (VisitRecord v : visits) {
                        double pct = (v.getVisitCount() * 100.0 / maxCount);
                %>
                    <div class="visit-bar">
                        <span class="ch-num">CH<%= v.getChannelNumber() %></span>
                        <span class="ch-name"><%= v.getChannelName() %></span>
                        <div class="bar-track"><div class="bar-fill" style="width:<%= pct %>%"></div></div>
                        <span class="count"><%= v.getVisitCount() %> visits</span>
                    </div>
                <%
                    }
                }
            } else {
        %>
            <p style="color:var(--danger);font-size:.875rem">⚠️ Database is offline. Start MySQL and restart the server to see visit history.</p>
        <% } %>
    </div>

    <!-- Commercials Management -->
    <div class="card">
        <h2>📺 Commercial Ad Management</h2>
        <%
            if (dbOk) {
                CommercialDAO commercialDAO = new CommercialDAO();
                List<Commercial> ads = commercialDAO.getAllCommercials();
                if (ads.isEmpty()) {
        %>
            <p style="color:var(--muted);font-size:.875rem">No commercials in the database.</p>
        <% } else { %>
            <div id="commercialList">
                <% for (Commercial ad : ads) { %>
                    <div class="commercial-item" id="ad-<%= ad.getId() %>">
                        <div class="title"><%= ad.getTitle() %></div>
                        <div class="content"><%= ad.getContent() %></div>
                        <div class="meta">
                            <span class="target"><%= ad.getTargetChannel() != null ? "CH " + ad.getTargetChannel() : "ALL CHANNELS" %></span>
                            <span>⏱ <%= ad.getDurationSeconds() %>s</span>
                            <span><%= ad.isActive() ? "✅ Active" : "❌ Inactive" %></span>
                        </div>
                        <div style="margin-top:8px">
                            <button class="btn btn-danger" onclick="deleteAd(<%= ad.getId() %>)">Delete</button>
                        </div>
                    </div>
                <% } %>
            </div>
        <% }
        } else { %>
            <p style="color:var(--danger);font-size:.875rem">⚠️ Database is offline. Start MySQL to manage commercials.</p>
        <% } %>

        <!-- Add commercial form (shown when DB is up) -->
        <% if (dbOk) { %>
        <div class="commercial-form">
            <h3 style="color:var(--primary);font-family:'Orbitron',monospace;font-size:.8rem;text-transform:uppercase">+ Add New Commercial</h3>
            <div>
                <div class="label">Title</div>
                <input type="text" id="adTitle" placeholder="e.g. Summer Sale" />
            </div>
            <div>
                <div class="label">Content / Ad text</div>
                <textarea id="adContent" rows="2" placeholder="Ad description..."></textarea>
            </div>
            <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px">
                <div>
                    <div class="label">Target Channel (empty = all)</div>
                    <select id="adTargetChannel">
                        <option value="">All Channels</option>
                        <% for (com.simulator.model.Channel ch : TVServerEndpoint.getChannelCatalog()) { %>
                            <option value="<%= ch.getNumber() %>">CH <%= ch.getNumber() %> – <%= ch.getName() %></option>
                        <% } %>
                    </select>
                </div>
                <div>
                    <div class="label">Duration (seconds)</div>
                    <input type="number" id="adDuration" value="15" min="5" max="60" />
                </div>
            </div>
            <div>
                <button class="btn btn-primary" onclick="addCommercial()">+ Add Commercial</button>
            </div>
        </div>
        <% } %>
    </div>

    <!-- Channel catalog -->
    <div class="card">
        <h2>📡 Channel Catalog</h2>
        <div class="channel-grid">
            <%
                for (com.simulator.model.Channel ch : TVServerEndpoint.getChannelCatalog()) {
            %>
            <div class="channel-card">
                <div class="num">CH <%= ch.getNumber() %></div>
                <div class="name"><%= ch.getName() %></div>
                <div class="genre"><%= ch.getGenre() %></div>
            </div>
            <% } %>
        </div>
    </div>

    <!-- Quick-start guide -->
    <div class="card">
        <h2>🚀 Quick Start</h2>
        <ol style="list-style:decimal inside;display:grid;gap:10px;color:var(--muted);font-size:.875rem;line-height:1.6">
            <li>Open <a href="<%= request.getContextPath() %>/client-player.jsp" style="color:var(--primary)" target="_blank">client-player.jsp</a> in <strong>multiple browser windows</strong> to simulate multiple TVs.</li>
            <li>Launch the <strong>React PWA Remote</strong> (cd frontend-remote → npm run dev) on <code style="color:var(--primary)">http://localhost:5173</code>.</li>
            <li>Register / Login from the remote app (👤 icon). Default admin: <code style="color:var(--primary)">admin / admin123</code>.</li>
            <li>Use <strong>⭐ Favourites</strong> to save channels per user, and <strong>🛡 Parental Control</strong> to lock channels.</li>
            <li>Visit history is tracked automatically and shown above for recommendations.</li>
        </ol>
    </div>
</main>

<footer>Virtual Television Broadcast Simulator v2.0 · Built with Jakarta EE + WebSockets + React PWA + JDBC/MySQL</footer>

<script>
    // Live clock
    setInterval(() => {
        document.getElementById('srvTime').textContent = new Date().toLocaleString();
    }, 1000);
    document.getElementById('srvTime').textContent = new Date().toLocaleString();

    // ── Commercial management (AJAX calls) ──────────────────────────────────
    function addCommercial() {
        const title = document.getElementById('adTitle').value.trim();
        const content = document.getElementById('adContent').value.trim();
        const targetCh = document.getElementById('adTargetChannel').value;
        const duration = parseInt(document.getElementById('adDuration').value) || 15;
        if (!title) { alert('Title is required'); return; }

        fetch('<%= request.getContextPath() %>/api/commercials', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-Auth-Token': getCookie('tv_admin_token') || '' },
            body: JSON.stringify({
                title, content, durationSeconds: duration,
                targetChannel: targetCh ? parseInt(targetCh) : null,
                active: true
            })
        }).then(r => r.json()).then(data => {
            alert('Commercial added!');
            location.reload();
        }).catch(e => alert('Error: ' + e.message));
    }

    function deleteAd(id) {
        if (!confirm('Delete this commercial?')) return;
        fetch('<%= request.getContextPath() %>/api/commercials?id=' + id, {
            method: 'DELETE',
            headers: { 'X-Auth-Token': getCookie('tv_admin_token') || '' }
        }).then(r => r.json()).then(data => {
            alert('Deleted');
            location.reload();
        }).catch(e => alert('Error: ' + e.message));
    }

    function getCookie(name) {
        const v = document.cookie.match('(^|;)\\s*' + name + '\\s*=\\s*([^;]+)');
        return v ? v.pop() : '';
    }
</script>
</body>
</html>
