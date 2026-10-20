# 📺 Virtual Television Broadcast Simulator

A full-stack, production-grade broadcast simulation platform featuring:

- **Java WebSocket backend** (Jakarta EE / Tomcat 10+)
- **Multi-client TV player** (HTML5 `<video>` + HLS.js via JSP)
- **PWA Remote Control** (React 18 + Vite 5 + Tailwind CSS)

---

## 🏗️ Project Structure

```
tv-broadcast-simulator/
├── backend/                         # Java Maven WAR project
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/simulator/
│       │   ├── controller/
│       │   │   ├── ChannelController.java   # REST API: /api/channels, /api/clients
│       │   │   └── DashboardServlet.java    # Forwards / → dashboard.jsp
│       │   ├── websocket/
│       │   │   └── TVServerEndpoint.java    # Central WS hub @ /ws/tv
│       │   └── model/
│       │       ├── Channel.java             # Channel data model
│       │       ├── ClientState.java         # Per-TV session state
│       │       └── WsMessage.java           # WS message DTO
│       └── webapp/
│           ├── WEB-INF/
│           │   ├── web.xml
│           │   └── views/
│           │       ├── dashboard.jsp        # Server admin dashboard
│           │       └── error.jsp            # 404/500 error page
│           ├── client-player.jsp            # TV client with HLS video player
│           └── index.jsp                    # Redirects → dashboard
└── frontend-remote/                 # React PWA Remote Control
    ├── package.json
    ├── vite.config.js
    ├── tailwind.config.js
    ├── postcss.config.js
    ├── index.html
    ├── public/
    │   ├── manifest.json            # PWA manifest
    │   ├── sw.js                    # Service Worker
    │   └── favicon.svg
    └── src/
        ├── main.jsx                 # React entry point
        ├── App.jsx                  # Remote control UI
        ├── index.css                # Tailwind + custom styles
        └── hooks/
            └── useWebSocket.js      # WebSocket hook with auto-reconnect
```

---

## ⚙️ Prerequisites

| Tool          | Minimum Version | Notes                                        |
|---------------|-----------------|----------------------------------------------|
| Java JDK      | 11+             | 17 or 21 recommended                         |
| Apache Maven  | 3.8+            | `mvn -v` to check                            |
| Apache Tomcat | **10.1+**       | Must be Tomcat 10+ for `jakarta.*` namespace |
| Node.js       | 18+             | `node -v` to check                           |
| npm           | 9+              | `npm -v` to check                            |

> ⚠️ **Tomcat 10+ is required.** Tomcat 9 uses the older `javax.*` namespace and is NOT compatible with this project's `jakarta.*` imports.

---

## 🚀 Setup & Running

### Step 1 – Build the Backend WAR

```bash
cd tv-broadcast-simulator/backend
mvn clean package -DskipTests
```

This produces:
```
backend/target/tv-broadcast-simulator.war
```

---

### Step 2 – Deploy to Apache Tomcat

**Option A – Copy WAR manually:**
1. Copy `target/tv-broadcast-simulator.war` into `$TOMCAT_HOME/webapps/`
2. Start Tomcat: `$TOMCAT_HOME/bin/startup.sh` (Linux/Mac) or `startup.bat` (Windows)
3. Tomcat auto-deploys the WAR to context path `/tv-broadcast-simulator`

**Option B – Tomcat Manager:**
1. Open `http://localhost:8080/manager/html`
2. Deploy the WAR file using the "WAR file to deploy" section

**Verify backend is running:**
```
http://localhost:8080/tv-broadcast-simulator/          → Admin Dashboard
http://localhost:8080/tv-broadcast-simulator/api/status → API health check
http://localhost:8080/tv-broadcast-simulator/api/channels → Channel catalog JSON
```

---

### Step 3 – Install Frontend Dependencies

```bash
cd tv-broadcast-simulator/frontend-remote
npm install
```

---

### Step 4 – Run the React PWA Remote

```bash
npm run dev
```

Open: **http://localhost:5173**

> The Vite dev server proxies `/api/*` requests to Tomcat at `localhost:8080/tv-simulator/api/*` — no CORS issues.

---

### Step 5 – Open TV Client Windows

Open one or more instances of the TV client in separate browser windows/tabs:

```
http://localhost:8080/tv-broadcast-simulator/client-player.jsp
```

Each window represents a **separate TV client** with its own ID. You can:
- Resize and tile browser windows side-by-side
- Open them in different browsers (Chrome, Firefox, Edge) for true multi-client simulation

---

### Step 6 – Use the PWA Remote Control

1. Open `http://localhost:5173` in a browser
2. The remote auto-connects via WebSocket and discovers all connected TV clients
3. Use the **TARGET** dropdown to select a specific TV or "BROADCAST ALL"
4. Control the selected TV(s):
   - **Keypad 0-9**: Type channel digits (auto-commits after 1.5s, or press OK)
   - **CH ▲/▼**: Cycle channels one at a time
   - **VOL ▲/▼**: Adjust volume in 5% steps
   - **Mute**: Toggle audio mute
   - **Power**: Toggle TV power on/off
   - **Quick CH grid**: Directly jump to channels 1–10
   - **Broadcast All**: Send commands to every connected TV simultaneously

---

## 🔌 WebSocket Protocol

### Connection URL
```
ws://localhost:8080/tv-broadcast-simulator/ws/tv
```

### Message Format (JSON)
All messages follow this structure:
```json
{
  "type":        "REGISTER | COMMAND | STATE_UPDATE | CLIENT_LIST | ERROR | PING | PONG",
  "command":     "CHANNEL_SET | CHANNEL_UP | CHANNEL_DOWN | VOLUME_SET | VOLUME_UP | VOLUME_DOWN | MUTE_TOGGLE | POWER_TOGGLE",
  "clientId":    1,
  "channel":     5,
  "volume":      70,
  "powered":     true,
  "muted":       false,
  "payload":     "...",
  "clientLabel": "TV Client – localhost:8080",
  "timestamp":   "2024-01-01T00:00:00Z"
}
```

### Registration Flow

**TV Client registers:**
```json
{ "type": "REGISTER", "payload": "TV", "clientLabel": "TV Client – Port 8080" }
```

**Remote Control registers:**
```json
{ "type": "REGISTER", "payload": "REMOTE", "clientLabel": "PWA Remote Control" }
```

**Server responds with initial state + client list:**
```json
{ "type": "STATE_UPDATE", "clientId": 1, "channel": 1, "volume": 50, "powered": true, "muted": false }
{ "type": "CLIENT_LIST", "clients": [ { "clientId": 1, "clientLabel": "...", ... } ] }
```

### Sending Commands

**Target specific TV (clientId = 2):**
```json
{ "type": "COMMAND", "command": "CHANNEL_SET", "clientId": 2, "channel": 7 }
```

**Broadcast to all TVs (clientId = -1):**
```json
{ "type": "COMMAND", "command": "VOLUME_UP", "clientId": -1 }
```

### Available Commands

| Command          | Description                  | Extra Fields          |
|------------------|------------------------------|-----------------------|
| `CHANNEL_SET`    | Set to specific channel      | `"channel": 5`        |
| `CHANNEL_UP`     | Next channel                 | –                     |
| `CHANNEL_DOWN`   | Previous channel             | –                     |
| `VOLUME_SET`     | Set volume 0–100             | `"volume": 75`        |
| `VOLUME_UP`      | +5% volume                   | –                     |
| `VOLUME_DOWN`    | -5% volume                   | –                     |
| `MUTE_TOGGLE`    | Toggle mute on/off           | –                     |
| `POWER_TOGGLE`   | Toggle power on/off          | –                     |
| `POWER_ON`       | Force power on               | –                     |
| `POWER_OFF`      | Force power off              | –                     |

---

## 🌐 REST API Endpoints

All endpoints are served at `http://localhost:8080/tv-broadcast-simulator/`

| Method | Path            | Description                        |
|--------|-----------------|------------------------------------|
| GET    | `/api/channels` | List all 10 broadcast channels     |
| GET    | `/api/clients`  | List all connected TV client states|
| GET    | `/api/status`   | Server health & summary stats      |

**Example – Get all channels:**
```bash
curl http://localhost:8080/tv-broadcast-simulator/api/channels
```

---

## 📱 PWA Installation

The React remote can be installed as a native-like app:

**Desktop (Chrome/Edge):**
1. Open `http://localhost:5173`
2. Click the install icon (➕) in the browser address bar
3. Click "Install"

**Mobile (Android Chrome):**
1. Open `http://<your-ip>:5173` on your phone
2. Tap the ⋮ menu → "Add to Home Screen"

---

## 🔧 Configuration

### Change Backend URL
The React remote defaults to `ws://localhost:8080/tv-simulator/ws/tv`.
To change it:
1. Click the ⚙ settings icon in the remote app header
2. Enter your custom WebSocket URL
3. Click "Save & Reconnect"

### Production Build (Frontend)
```bash
cd frontend-remote
npm run build       # outputs to frontend-remote/dist/
npm run preview     # preview production build locally
```

Serve the `dist/` folder with any static web server (Nginx, Apache, Netlify, etc.)

### Adding More Channels
Edit the `buildChannelCatalog()` method in [`TVServerEndpoint.java`](backend/src/main/java/com/simulator/websocket/TVServerEndpoint.java) and add more `Channel` objects. Also update the `CHANNELS` array in [`App.jsx`](frontend-remote/src/App.jsx).

---

## 🧪 Testing Multi-Client Simulation

### Simulating Multiple TVs

1. Open 3 browser windows side by side:
   - **Window 1:** `http://localhost:5173` (Remote Control)
   - **Window 2:** `http://localhost:8080/tv-broadcast-simulator/client-player.jsp` (TV #1)
   - **Window 3:** `http://localhost:8080/tv-broadcast-simulator/client-player.jsp` (TV #2)

2. In the remote, the TARGET dropdown will show "TV #1" and "TV #2"

3. Select TV #1 → change channel to 3 → observe only TV #1 changes
4. Select TV #2 → change channel to 7 → observe only TV #2 changes
5. Select "BROADCAST ALL" → change channel → both TVs switch simultaneously

### WebSocket Testing with wscat

```bash
# Install wscat globally
npm install -g wscat

# Connect as a remote and send a command
wscat -c "ws://localhost:8080/tv-broadcast-simulator/ws/tv"
> {"type":"REGISTER","payload":"REMOTE","clientLabel":"wscat test"}
> {"type":"COMMAND","command":"CHANNEL_SET","clientId":-1,"channel":5}
```

---

## 🐛 Troubleshooting

| Problem | Solution |
|---------|----------|
| `javax.websocket` not found | Ensure you're using **Tomcat 10.1+** (Jakarta namespace). Tomcat 9 uses `javax.*`. |
| 404 on `/ws/tv` | Check the WAR deployed correctly; check `catalina.out` logs |
| Remote shows "No TV clients" | Open `client-player.jsp` in a separate browser window first |
| Video doesn't play | Browser autoplay policy may block it; click on the video area to interact |
| HLS stream not loading | Try a non-HLS (MP4) URL in the channel catalog for offline testing |
| PWA not installable | Must be served over HTTPS or localhost; check `manifest.json` is accessible |
| CORS errors in browser | Use the Vite proxy (`/api/*`) for development; set headers in `ChannelController` for production |

---

## 📦 Production Deployment Checklist

- [ ] Build backend: `mvn clean package`
- [ ] Build frontend: `cd frontend-remote && npm run build`
- [ ] Deploy WAR to production Tomcat (10.1+)
- [ ] Serve `frontend-remote/dist/` behind Nginx/Apache with HTTPS
- [ ] Update WebSocket URL in frontend to use `wss://` (secure WebSocket)
- [ ] Configure Nginx to proxy `/ws/tv` WebSocket upgrade requests to Tomcat
- [ ] Set up proper logging in `src/main/resources/simplelogger.properties`

---

## 📄 License

MIT License – free to use, modify, and distribute.

---

*Built with ❤️ using Jakarta EE, Java WebSockets, React, Vite, and Tailwind CSS*
