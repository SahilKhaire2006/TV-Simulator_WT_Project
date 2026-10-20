# Member 1 — WebSocket Communication & Real-Time Messaging

## Your Role in the Project

You built the **real-time communication backbone** of the entire TV Broadcast Simulator.
Without your part, no command from the remote would ever reach the TV screen.
Every button press, every channel change, every volume update flows through the system you built.

---

## What is WebSocket?

WebSocket is a protocol that keeps a **persistent two-way connection** open between a browser and a server.
Unlike normal HTTP (where the browser asks and the server replies once), WebSocket lets the server **push messages to the browser at any time** — perfect for a live TV simulator.

In this project:
- The **TV client** opens a WebSocket to receive commands (channel change, volume, power)
- The **PWA Remote** opens a WebSocket to send commands
- The **server** sits in the middle, routing messages between them

```
PWA Remote ──── WebSocket ────► SERVER ────► TV Client #1
                                     └──────► TV Client #2
                                     └──────► TV Client #3
```

---

## Files You Own

| File | Location | What it does |
|------|----------|--------------|
| `TVServerEndpoint.java` | `backend/src/main/java/com/simulator/websocket/` | Central WS hub — handles all connections and messages |
| `TVWebSocketConfig.java` | `backend/src/main/java/com/simulator/websocket/` | Registers the WebSocket endpoint with Tomcat |
| `WsMessage.java` | `backend/src/main/java/com/simulator/model/` | The message format (DTO) used for all communication |
| `client-player.jsp` | `backend/src/main/webapp/` | WebSocket client code inside the TV browser (connect + receive) |
| `useWebSocket.js` | `frontend-remote/src/hooks/` | WebSocket client code inside the React PWA remote (connect + send) |

---

## Deep Dive: TVServerEndpoint.java

This is the most important file in the entire project. It is a **Jakarta WebSocket Server Endpoint** — a Java class that Tomcat turns into a live WebSocket server.

### Annotation
```java
@ServerEndpoint(value = "/ws/tv")
```
This tells Tomcat: "any browser connecting to `ws://localhost:8085/tv-simulator/ws/tv` should be handled by this class."

### Session Storage
```java
private static final ConcurrentHashMap<String, ClientState> TV_SESSIONS = new ConcurrentHashMap<>();
private static final ConcurrentHashMap<String, Session>     ALL_SESSIONS = new ConcurrentHashMap<>();
private static final ConcurrentHashMap<Integer, String>     CLIENT_ID_MAP = new ConcurrentHashMap<>();
```
- `TV_SESSIONS` — stores the state of every connected TV (channel, volume, power, mute)
- `ALL_SESSIONS` — stores every connected session (TVs + Remotes)
- `CLIENT_ID_MAP` — maps a human-readable TV ID (1, 2, 3...) back to its session ID

`ConcurrentHashMap` is used because multiple browsers can connect at the same time — this is thread-safe.

### Lifecycle Methods

**@OnOpen** — fires when any browser connects:
```java
@OnOpen
public void onOpen(Session session) {
    ALL_SESSIONS.put(session.getId(), session);
    sendToSession(session, "{\"type\":\"HELLO\"}");
}
```
Every new connection gets stored and receives a HELLO message asking it to register.

**@OnMessage** — fires when a browser sends a message:
```java
@OnMessage
public void onMessage(String rawJson, Session sender) {
    WsMessage msg = GSON.fromJson(rawJson, WsMessage.class);
    switch (msg.getType()) {
        case "REGISTER" -> handleRegister(msg, sender);
        case "COMMAND"  -> handleCommand(msg, sender);
        case "PING"     -> sendToSession(sender, GSON.toJson(WsMessage.pong()));
    }
}
```

**@OnClose** — fires when a browser disconnects:
```java
@OnClose
public void onClose(Session session, CloseReason reason) {
    ALL_SESSIONS.remove(session.getId());
    ClientState cs = TV_SESSIONS.remove(session.getId());
    if (cs != null) {
        CLIENT_ID_MAP.remove(cs.getClientId());
        broadcastClientList(); // tell all remotes a TV left
    }
}
```

### Registration Flow

When a TV browser opens, it sends:
```json
{ "type": "REGISTER", "payload": "TV", "clientLabel": "Living Room TV #195" }
```

The server:
1. Creates a `ClientState` object for this TV (assigns it ID 1, 2, 3...)
2. Stores it in `TV_SESSIONS`
3. Sends back the initial state (CH 1, VOL 50, powered ON)
4. Broadcasts updated client list to all remote controls

When a PWA Remote opens, it sends:
```json
{ "type": "REGISTER", "payload": "REMOTE", "clientLabel": "PWA Remote Control" }
```
The server does NOT store state for remotes — they just receive CLIENT_LIST updates.

### Command Routing

```java
private void handleCommand(WsMessage msg, Session sender) {
    int targetId = msg.getClientId();

    if (targetId == -1) {
        // Broadcast to ALL TVs
        TV_SESSIONS.forEach((sid, cs) -> applyCommand(cmd, msg, cs, ALL_SESSIONS.get(sid)));
    } else {
        // Target one specific TV
        String sid = CLIENT_ID_MAP.get(targetId);
        applyCommand(cmd, msg, TV_SESSIONS.get(sid), ALL_SESSIONS.get(sid));
    }
}
```
`clientId = -1` means "broadcast to everyone". Any positive number targets that specific TV.

### applyCommand — Where State Changes Happen

```java
switch (cmd) {
    case "CHANNEL_SET"   -> cs.setCurrentChannel(Math.max(1, Math.min(99, msg.getChannel())));
    case "CHANNEL_UP"    -> cs.setCurrentChannel(next > maxChannel ? 1 : next);
    case "CHANNEL_DOWN"  -> cs.setCurrentChannel(prev < 1 ? maxChannel : prev);
    case "VOLUME_UP"     -> cs.setVolume(cs.getVolume() + 5);
    case "VOLUME_DOWN"   -> cs.setVolume(cs.getVolume() - 5);
    case "MUTE_TOGGLE"   -> cs.setMuted(!cs.isMuted());
    case "POWER_TOGGLE"  -> cs.setPowered(!cs.isPowered());
}
```
After mutating state, it sends `STATE_UPDATE` back to the TV and to all remotes so their LCD display refreshes.

---

## Deep Dive: WsMessage.java

This is the **message envelope** — every single message sent over WebSocket is serialized/deserialized as this object using Gson.

### Key fields:
```java
private String  type;         // "REGISTER", "COMMAND", "STATE_UPDATE", "PING", "PONG", etc.
private String  command;      // "CHANNEL_SET", "VOLUME_UP", "MUTE_TOGGLE", etc.
private int     clientId;     // which TV to target (-1 = all)
private int     channel;      // channel number
private int     volume;       // 0–100
private boolean powered;
private boolean muted;
private String  payload;      // extra string data
private long    serverTime;   // server's current timestamp (for clock sync)
private long    broadcastEpoch; // fixed server-start time (for broadcast sync)
```

### Message Types you handle:

| Type | Direction | Meaning |
|------|-----------|---------|
| `HELLO` | Server → Client | "You connected, please register" |
| `REGISTER` | Client → Server | "I am a TV / I am a Remote" |
| `COMMAND` | Remote → Server | "Change channel / volume / power" |
| `STATE_UPDATE` | Server → TV + Remote | "Here is the new state after command" |
| `CLIENT_LIST` | Server → Remote | "Here are all connected TVs" |
| `PING` | Client → Server | "Are you still alive?" |
| `PONG` | Server → Client | "Yes, still alive" |

---

## Deep Dive: useWebSocket.js (PWA Remote side)

```javascript
const connect = useCallback(() => {
    const ws = new WebSocket(url);

    ws.onopen = () => {
        ws.send(JSON.stringify({
            type: 'REGISTER',
            payload: 'REMOTE',
            clientLabel: 'PWA Remote Control'
        }));
        // Start keepalive ping every 25 seconds
        pingTimer.current = setInterval(() => {
            ws.send(JSON.stringify({ type: 'PING' }))
        }, 25000);
    };

    ws.onclose = () => {
        // Auto-reconnect after 3.5 seconds
        reconnectTimer.current = setTimeout(connect, 3500);
    };
}, [url]);
```

Key features you built here:
- **Auto-reconnect** — if the server restarts or network drops, the remote reconnects automatically
- **Keepalive ping** — prevents the connection from timing out during inactivity
- **Status tracking** — `connecting → connected → disconnected` shown in the UI

---

## Deep Dive: client-player.jsp (TV WebSocket side)

```javascript
function connect() {
    ws = new WebSocket(WS_URL.url);

    ws.onopen = () => {
        ws.send(JSON.stringify({
            type: 'REGISTER',
            payload: 'TV',
            clientLabel: WS_URL.label   // e.g. "Living Room TV #195"
        }));
    };

    ws.onmessage = (evt) => {
        const msg = JSON.parse(evt.data);
        switch (msg.type) {
            case 'STATE_UPDATE': applyState({...}); break;
            case 'PONG': break;
        }
    };

    ws.onclose = () => {
        reconnectTimer = setTimeout(connect, 4000); // auto-reconnect
    };
}
```

The TV label is randomly generated and stored in `sessionStorage` so the same tab keeps the same name across page refreshes:
```javascript
const presets = ['Living Room TV', 'Bedroom TV', 'Kitchen TV', 'Sports Bar TV', 'Lounge TV'];
stored = presets[randIdx] + ' #' + Math.floor(100 + Math.random() * 900);
sessionStorage.setItem('tv_custom_label', stored);
```

---

## The Full Message Flow (End to End)

```
User presses "CH 5" on Remote
         │
         ▼
App.jsx sends via WebSocket:
{ "type": "COMMAND", "command": "CHANNEL_SET", "clientId": 2, "channel": 5 }
         │
         ▼
TVServerEndpoint.onMessage() receives it
         │
         ▼
handleCommand() → finds TV with clientId=2 → calls applyCommand()
         │
         ▼
ClientState for TV #2: currentChannel = 5
         │
         ├──► Sends STATE_UPDATE to TV #2:
         │    { "type": "STATE_UPDATE", "channel": 5, "volume": 50, ... }
         │
         └──► Sends STATE_UPDATE to all Remotes (LCD refreshes)

TV #2 browser receives STATE_UPDATE
         │
         ▼
applyState() → loadStream(5) → video switches to ch5.mp4
```

---

## What to Say During Presentation

> "I built the WebSocket layer that connects everything together. When you press a button on the remote, my code on the server receives that message, figures out which TV to target, updates its state, and immediately pushes the new state back to the TV screen — all within milliseconds. I also built both WebSocket clients: the one inside the TV browser that registers and listens for commands, and the hook inside the React remote that sends commands and auto-reconnects if the network drops."

---

## Key Concepts to Know

- **WebSocket vs HTTP** — persistent vs request/response
- **@ServerEndpoint** — Jakarta EE annotation that creates a WS server
- **ConcurrentHashMap** — thread-safe map for handling multiple simultaneous connections
- **Gson** — Java library for converting Java objects ↔ JSON strings
- **Session** — represents one open WebSocket connection
- **Broadcast** — sending a message to all connected clients at once
- **Auto-reconnect** — client retries connection after disconnect
