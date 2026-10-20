# Member 3 — TV Client Screen & Admin Dashboard

## Your Role in the Project

You built **what people actually see** — both the TV screen that viewers watch, and the admin panel that monitors the entire system.

Your work covers two distinct UIs:
1. **TV Client** (`client-player.jsp`) — the simulated television screen with video, overlays, and status bar
2. **Admin Dashboard** (`dashboard.jsp`) — the server operator's control panel showing all connected TVs and channels

You also built the **REST API** that serves live data to both the dashboard and any external tools.

---

## Files You Own

| File | Location | What it does |
|------|----------|--------------|
| `client-player.jsp` | `backend/src/main/webapp/` | Full TV screen UI — bezel, video, overlays, status bar |
| `dashboard.jsp` | `backend/src/main/webapp/WEB-INF/views/` | Admin dashboard — server stats, TV table, channel grid |
| `DashboardServlet.java` | `backend/src/main/java/com/simulator/controller/` | Routes `/` and `/dashboard` to dashboard.jsp |
| `ChannelController.java` | `backend/src/main/java/com/simulator/controller/` | REST API: `/api/channels`, `/api/clients`, `/api/status` |
| `ClientState.java` | `backend/src/main/java/com/simulator/model/` | Stores per-TV state: channel, volume, power, mute |
| `web.xml` | `backend/src/main/webapp/WEB-INF/` | Servlet mappings and app configuration |

---

## Part A: TV Client Screen

### Overview

`client-player.jsp` is a JSP (JavaServer Pages) file — it runs on the server to inject dynamic data (like the channel catalog), then delivers a complete HTML page to the browser. The actual TV experience is driven by JavaScript in the page.

### The TV Bezel Layout

```
┌─────────────────────────────────────────┐
│  TV Header Badge    📺 Living Room #195  │  ← top-right badge
│                                         │
│  ┌───────────────────────────────────┐  │
│  │                                   │  │
│  │         VIDEO PLAYER              │  │  ← main screen
│  │         (HTML5 <video>)           │  │
│  │                                   │  │
│  │  [Channel Banner]  CH 04          │  │  ← slides up from bottom
│  │  DocuWorld • Nature               │  │
│  └───────────────────────────────────┘  │
│  WS●Connected  ID:2  CH:4  VOL:60  News │  ← status bar
└─────────────────────────────────────────┘
```

### CSS: The TV Bezel

```css
.tv-bezel {
    flex: 1;
    display: flex;
    flex-direction: column;
    background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
    border: 3px solid #2d2d44;
    border-radius: 16px;
    margin: 12px;
    box-shadow: 0 0 60px rgba(0,0,0,0.8), inset 0 0 30px rgba(0,0,0,0.5);
    overflow: hidden;
}
```

The dark gradient and inset shadow create the illusion of a physical TV frame.

### CSS: CRT Scanline Effect

```css
.screen-container::after {
    content: '';
    position: absolute;
    inset: 0;
    background: repeating-linear-gradient(
        0deg,
        transparent, transparent 2px,
        rgba(0,0,0,0.05) 2px, rgba(0,0,0,0.05) 4px
    );
    pointer-events: none;
    z-index: 10;
}
```

This CSS pseudo-element overlays horizontal lines across the entire screen — simulating the scanlines of an old CRT television. `pointer-events: none` means clicks pass through it to the video below.

### Channel Banner Overlay

The channel banner slides up from the bottom when you switch channels:

```css
.channel-banner {
    position: absolute;
    bottom: 70px;
    left: 20px; right: 20px;
    opacity: 0;
    transform: translateY(20px);
    transition: opacity 0.4s ease, transform 0.4s ease;
}
.channel-banner.visible {
    opacity: 1;
    transform: translateY(0);
}
```

JavaScript controls it:
```javascript
function showChannelBanner(ch) {
    const chInfo = getChannelInfo(ch);
    bannerChannelNum.textContent   = String(ch).padStart(2, '0');
    bannerChannelName.textContent  = chInfo.name;
    bannerChannelGenre.textContent = chInfo.genre + ' • ' + chInfo.description;

    channelBanner.classList.add('visible');
    clearTimeout(bannerTimer);
    bannerTimer = setTimeout(() => channelBanner.classList.remove('visible'), 4000);
}
```
It shows for 4 seconds then fades out automatically.

### Volume Overlay

```javascript
function showVolumeOverlay() {
    const pct = tvState.muted ? 0 : tvState.volume;
    volumeBarFill.style.width = pct + '%';
    volumeLabel.textContent   = tvState.muted ? '🔇' : tvState.volume;
    volumeOverlay.classList.add('visible');
    clearTimeout(volumeTimer);
    volumeTimer = setTimeout(() => volumeOverlay.classList.remove('visible'), 2500);
}
```
Appears in the top-right for 2.5 seconds whenever volume changes.

### Off-Screen (Power Off State)

```html
<div class="off-screen" id="offScreen">
    <div class="power-icon">⏻</div>
    <p>TV is powered off</p>
</div>
```

```javascript
if (!tvState.powered) {
    offScreen.classList.add('visible');   // show black screen with ⏻
    video.pause();
    video.src = '';
} else {
    offScreen.classList.remove('visible'); // show video
}
```

### applyState() — The State Machine

This is the central function that applies any incoming state update to the entire TV UI:

```javascript
function applyState(state) {
    const channelChanged = state.channel !== tvState.channel;
    const volumeChanged  = state.volume  !== tvState.volume;
    tvState = { ...tvState, ...state };

    // Power
    if (!tvState.powered) {
        offScreen.classList.add('visible');
        video.pause(); video.src = '';
    } else {
        offScreen.classList.remove('visible');
        if (channelChanged) {
            loadStream(tvState.channel);     // load new video
            showChannelBanner(tvState.channel);
        }
    }

    // Volume
    video.volume = tvState.volume / 100;
    video.muted  = tvState.muted;
    muteBadge.classList.toggle('visible', tvState.muted);

    if (volumeChanged) showVolumeOverlay();

    // Status bar
    statusChannel.textContent = tvState.channel;
    statusVolume.textContent  = tvState.volume;
    statusGenre.textContent   = getChannelInfo(tvState.channel).genre;
}
```

### JSP: Channel Catalog Injection

The channel list is injected from Java into JavaScript at page load time:

```jsp
<%
    List<Channel> channels = TVServerEndpoint.getChannelCatalog();
    StringBuilder chJson = new StringBuilder("[");
    for (int i = 0; i < channels.size(); i++) {
        Channel ch = channels.get(i);
        chJson.append("{")
              .append("\"number\":").append(ch.getNumber()).append(",")
              .append("\"name\":\"").append(ch.getName()).append("\",")
              .append("\"streamUrl\":\"").append(ch.getStreamUrl()).append("\"")
              .append("}");
        if (i < channels.size() - 1) chJson.append(",");
    }
    chJson.append("]");
%>
<script>
const CHANNEL_CATALOG = <%= chJson.toString() %>;
</script>
```

This means the browser doesn't need to make a separate API call — channel data is embedded in the page HTML itself.

### Status Bar

```html
<div class="status-bar">
    <div class="status-item">
        <div class="dot" id="wsDot"></div>
        <span class="status-val" id="wsStatus">Connecting…</span>
    </div>
    <div class="status-item">
        <span>CH</span>
        <span class="status-val" id="statusChannel">1</span>
    </div>
    <div class="status-item">
        <span>VOL</span>
        <span class="status-val" id="statusVolume">50</span>
    </div>
    <div class="status-item">
        <span id="statusTime"></span>
    </div>
</div>
```

The blinking dot turns red when WebSocket disconnects. The clock ticks every second via `setInterval`.

---

## Part B: Admin Dashboard

### Overview

`dashboard.jsp` is the server operator's view — it shows real-time stats about the system. It uses **JSP scriptlets** (Java code inside `<% %>` tags) to pull live data directly from the server's memory when the page loads.

### Live Data via JSP Scriptlets

```jsp
<%@ page import="com.simulator.websocket.TVServerEndpoint" %>
<%@ page import="com.simulator.model.ClientState" %>
<%@ page import="java.util.Map" %>

<!-- Connected TV count: read directly from server memory -->
<span class="stat-val"><%= TVServerEndpoint.getTvSessions().size() %></span>

<!-- Channel count -->
<span class="stat-val"><%= TVServerEndpoint.getChannelCatalog().size() %></span>
```

This is different from REST API calls — the JSP runs **on the server**, so `TVServerEndpoint.getTvSessions()` is a direct Java method call, not a network request.

### Connected TVs Table

```jsp
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
```

Each row is one connected TV. The data is server-fresh on every page load.

### Channel Catalog Grid

```jsp
<% for (com.simulator.model.Channel ch : TVServerEndpoint.getChannelCatalog()) { %>
<div class="channel-card">
    <div class="num">CH <%= ch.getNumber() %></div>
    <div class="name"><%= ch.getName() %></div>
    <div class="genre"><%= ch.getGenre() %></div>
</div>
<% } %>
```

Shows all 24 channels in a responsive card grid.

---

## Part C: REST API

### ChannelController.java

```java
@WebServlet(urlPatterns = {"/api/channels", "/api/clients", "/api/status"})
public class ChannelController extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Access-Control-Allow-Origin", "*");  // CORS for React frontend

        switch (req.getServletPath()) {
            case "/api/channels" -> out.print(GSON.toJson(buildChannelResponse()));
            case "/api/clients"  -> out.print(GSON.toJson(buildClientResponse()));
            case "/api/status"   -> out.print(GSON.toJson(buildStatusResponse()));
        }
    }
}
```

### API Endpoints

**GET /api/channels** — returns all 24 channels:
```json
[
  { "number": 1, "name": "NovaStar News", "genre": "News", "streamUrl": "..." },
  { "number": 2, "name": "Galaxy Cinema", "genre": "Movies", "streamUrl": "..." },
  ...
]
```

**GET /api/clients** — returns all connected TVs:
```json
[
  { "clientId": 1, "clientLabel": "Living Room TV #195", "channel": 4, "volume": 60, "powered": true },
  { "clientId": 2, "clientLabel": "Bedroom TV #958",     "channel": 6, "volume": 40, "powered": true }
]
```

**GET /api/status** — health check:
```json
{ "status": "OK", "totalChannels": 24, "connectedClients": 2, "serverTime": "..." }
```

CORS header `Access-Control-Allow-Origin: *` is set so the React frontend (running on port 5173) can call these endpoints from a different port without browser errors.

---

## Part D: ClientState.java

This model class tracks the live state of each connected TV:

```java
public class ClientState {
    private static final AtomicInteger ID_COUNTER = new AtomicInteger(1);

    private final int    clientId;      // auto-assigned: 1, 2, 3...
    private final String sessionId;     // WebSocket session ID
    private final Instant connectedAt;

    private volatile int     currentChannel;  // 1–24
    private volatile int     volume;          // 0–100
    private volatile boolean powered;
    private volatile boolean muted;
    private volatile String  clientLabel;
}
```

`volatile` keyword ensures changes made by one thread are immediately visible to other threads — important since WebSocket messages can arrive concurrently.

`AtomicInteger` for the ID counter means even if two TVs connect at the exact same millisecond, they get different IDs.

---

## Part E: web.xml

```xml
<web-app>
    <!-- Default servlet for static files (videos, assets) -->
    <servlet-mapping>
        <servlet-name>default</servlet-name>
        <url-pattern>/videos/*</url-pattern>
        <url-pattern>/assets/*</url-pattern>
    </servlet-mapping>

    <!-- Welcome file -->
    <welcome-file-list>
        <welcome-file>index.jsp</welcome-file>
    </welcome-file-list>
</web-app>
```

This config tells Tomcat how to route requests and which file to serve when someone opens the root URL.

---

## What to Say During Presentation

> "I built two things: the TV screen interface and the admin dashboard. The TV screen has the video player, overlays for channel changes and volume, a power-off state, and a status bar. I used JSP to inject the channel catalog from the server directly into the page so no extra API call is needed. The admin dashboard lets the server operator see all connected TVs in real time — how many are connected, which channel each one is on, and whether they're muted. I also built the REST API with three endpoints that expose channel and client data as JSON, which the React frontend uses too."

---

## Key Concepts to Know

- **JSP (JavaServer Pages)** — HTML files with embedded Java code (`<% %>`) that run on the server
- **Scriptlet** — the `<% %>` block inside JSP that executes Java at request time
- **@WebServlet** — annotation that maps a Java class to a URL path
- **REST API** — HTTP endpoints that return JSON data
- **CORS** — Cross-Origin Resource Sharing; the `Access-Control-Allow-Origin: *` header allows the React app to call the API
- **volatile** — Java keyword ensuring thread visibility of variable changes
- **AtomicInteger** — thread-safe integer counter
- **CSS pseudo-element (::after)** — creates the CRT scanline overlay without extra HTML
- **CSS transition** — animates the channel banner sliding up and fading in
