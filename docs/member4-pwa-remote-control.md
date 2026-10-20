# Member 4 — PWA Remote Control

## Your Role in the Project

You built the **remote control** — the interface the user holds in their hand (or opens on their phone) to control the TV.

It is a **Progressive Web App (PWA)** built with React 18, meaning it works like a website but can be **installed on a phone or desktop like a native app** — no App Store needed.

Your remote connects to the server via WebSocket, discovers all connected TVs, and lets the user send commands to one TV or all TVs at once.

---

## Files You Own

| File | Location | What it does |
|------|----------|--------------|
| `App.jsx` | `frontend-remote/src/` | The entire remote control UI and logic |
| `useWebSocket.js` | `frontend-remote/src/hooks/` | WebSocket hook with auto-reconnect and keepalive |
| `index.css` | `frontend-remote/src/` | All custom styles: remote shell, LCD, buttons |
| `main.jsx` | `frontend-remote/src/` | React app entry point |
| `index.html` | `frontend-remote/` | HTML shell for the React app |
| `manifest.json` | `frontend-remote/public/` | PWA metadata: name, icons, theme color |
| `sw.js` | `frontend-remote/public/` | Service worker for offline + installability |
| `package.json` | `frontend-remote/` | npm dependencies and scripts |
| `vite.config.js` | `frontend-remote/` | Build tool config + dev server proxy |
| `tailwind.config.js` | `frontend-remote/` | Tailwind CSS design tokens |

---

## Tech Stack You Used

| Tool | Purpose |
|------|---------|
| **React 18** | UI framework — components, state, hooks |
| **Vite 5** | Build tool and dev server (hot reload) |
| **Tailwind CSS** | Utility-first CSS framework |
| **react-hot-toast** | Toast notification library |
| **PWA (manifest + service worker)** | Makes the app installable |

---

## Deep Dive: App.jsx

This is the largest and most important file you own. It manages all state and renders the entire remote UI.

### State Variables

```javascript
const [wsUrl, setWsUrl]           = useState(DEFAULT_WS_URL)   // WebSocket server URL
const [clients, setClients]       = useState([])                 // connected TV list
const [targetId, setTargetId]     = useState(-1)                 // -1 = all, >0 = specific TV
const [clientStates, setClientStates] = useState({})             // state of each TV
const [digitBuffer, setDigitBuffer]   = useState('')             // typed channel digits
const [pressedKey, setPressedKey]     = useState(null)           // button press animation
const [clock, setClock]           = useState(new Date())         // live clock display
```

### The Target System

`targetId` is the key concept of the whole remote:
- `-1` → send command to **ALL TVs** simultaneously
- `2` → send command to **only TV #2**

```javascript
const sendCommand = useCallback((command, extra = {}) => {
    send({
        type:     'COMMAND',
        command,
        clientId: targetId,   // -1 or specific TV id
        ...extra,
    })
}, [send, targetId])
```

The user changes the target using the TARGET dropdown in the LCD panel.

### Receiving Messages

```javascript
const handleMessage = useCallback((msg) => {
    switch (msg.type) {

        case 'CLIENT_LIST':
            // Server sent an updated list of connected TVs
            setClients(msg.clients ?? [])
            setClientStates(prev => {
                const next = { ...prev }
                msg.clients.forEach(c => { next[c.clientId] = c })
                return next
            })
            break

        case 'STATE_UPDATE':
            // A TV's state changed — update the mirror
            setClientStates(prev => ({
                ...prev,
                [msg.clientId]: {
                    clientId: msg.clientId,
                    channel:  msg.channel,
                    volume:   msg.volume,
                    powered:  msg.powered,
                    muted:    msg.muted,
                }
            }))
            // Also update the clients list so dropdown reflects current channel
            setClients(prev =>
                prev.map(c => c.clientId === msg.clientId
                    ? { ...c, channel: msg.channel, volume: msg.volume }
                    : c
                )
            )
            break

        case 'ERROR':
            toast.error('Server: ' + msg.payload)
            break
    }
}, [targetId])
```

`clientStates` is a **mirror** of the server's TV states, kept in sync via STATE_UPDATE messages. This is what drives the LCD display.

### LCD Display

The LCD panel shows the current state of the selected TV:

```javascript
const currentState   = clientStates[targetId] ?? null
const displayChannel = currentState?.channel ?? '––'
const displayVolume  = currentState?.volume  ?? '––'
const displayPowered = currentState?.powered ?? true
const displayMuted   = currentState?.muted   ?? false
const chInfo         = currentState ? getChannelInfo(currentState.channel) : { name: '–' }
```

These variables feed the JSX:
```jsx
<div className="lcd-text text-4xl font-black">
    {digitBuffer ? digitBuffer.padStart(2, '_') : padChannel(displayChannel)}
</div>
<div className="text-primary/70 text-xs font-mono">{chInfo.name}</div>
```

When `digitBuffer` is non-empty (user is typing a channel), it shows the typed digits live. Otherwise shows current channel.

---

### Digit Accumulation — Numeric Channel Entry

This is one of the cleverer features you built. Pressing `2` then `4` should set channel 24, not channel 2 then channel 4.

```javascript
const handleDigit = useCallback((digit) => {
    pressButton(digit, () => {
        setDigitBuffer(prev => {
            const next = prev + digit          // append digit
            clearTimeout(digitTimer.current)   // reset the timer

            // Auto-commit after 1.5 seconds of no more digits
            digitTimer.current = setTimeout(() => {
                const ch = parseInt(next, 10)
                if (ch >= 1 && ch <= 99) {
                    sendCommand('CHANNEL_SET', { channel: ch })
                    toast(`📺 CH ${padChannel(ch)} ${chInfo.name}`)
                }
                setDigitBuffer('')
            }, 1500)

            return next.length > 2 ? digit : next  // max 2 digits
        })
    })
}, [pressButton, sendCommand])
```

Flow:
1. User presses `2` → buffer = `"2"`, timer starts (1.5s)
2. User presses `4` → buffer = `"24"`, timer resets
3. 1.5s passes without another digit → commits CH 24
4. Or user presses OK immediately → commits right away

The floating indicator above the remote shows the buffer live:
```jsx
{digitBuffer && (
    <div className="fixed top-6 left-1/2 -translate-x-1/2 ... z-50">
        CH {digitBuffer.padStart(2, '0')}
    </div>
)}
```

---

### Button Press Animation

```javascript
const pressButton = useCallback((key, fn) => {
    setPressedKey(key)
    setTimeout(() => setPressedKey(null), 200)
    fn()
}, [])
```

Sets `pressedKey` for 200ms, which applies a `.pressed` CSS class to the button — giving it a tactile "push down" effect.

```jsx
<button className={`btn-num h-16 ${pressedKey === String(d) ? 'pressed' : ''}`}>
```

---

### Keyboard Shortcuts

```javascript
useEffect(() => {
    const handleKey = (e) => {
        if (showSettings || showInfo) return  // ignore when panels are open
        if (e.key >= '0' && e.key <= '9') { handleDigit(e.key); return }
        switch (e.key) {
            case 'ArrowUp':   sendCommand('CHANNEL_UP');   break
            case 'ArrowDown': sendCommand('CHANNEL_DOWN'); break
            case '+':         sendCommand('VOLUME_UP');    break
            case '-':         sendCommand('VOLUME_DOWN');  break
            case 'm': case 'M': sendCommand('MUTE_TOGGLE');  break
            case 'p': case 'P': sendCommand('POWER_TOGGLE'); break
        }
    }
    window.addEventListener('keydown', handleKey)
    return () => window.removeEventListener('keydown', handleKey)
}, [handleDigit, pressButton, sendCommand, showSettings, showInfo])
```

The `return () => window.removeEventListener(...)` is the **cleanup function** — React calls it when the component unmounts to prevent memory leaks.

---

## Deep Dive: useWebSocket.js

This custom hook encapsulates all WebSocket logic so `App.jsx` just calls `useWebSocket(url, onMessage)` and gets back `{ status, send }`.

```javascript
export function useWebSocket(url, onMessage) {
    const wsRef          = useRef(null)
    const reconnectTimer = useRef(null)
    const pingTimer      = useRef(null)
    const [status, setStatus] = useState('disconnected')

    const connect = useCallback(() => {
        setStatus('connecting')
        const ws = new WebSocket(url)
        wsRef.current = ws

        ws.onopen = () => {
            setStatus('connected')
            ws.send(JSON.stringify({
                type: 'REGISTER', payload: 'REMOTE', clientLabel: 'PWA Remote Control'
            }))
            // Keepalive: ping every 25 seconds
            pingTimer.current = setInterval(() => {
                if (ws.readyState === WebSocket.OPEN)
                    ws.send(JSON.stringify({ type: 'PING' }))
            }, 25000)
        }

        ws.onclose = () => {
            setStatus('disconnected')
            clearInterval(pingTimer.current)
            reconnectTimer.current = setTimeout(connect, 3500)  // auto-reconnect
        }
    }, [url])

    const send = useCallback((data) => {
        if (wsRef.current?.readyState === WebSocket.OPEN) {
            wsRef.current.send(JSON.stringify(data))
        }
    }, [])

    return { status, send, ws: wsRef }
}
```

**Why `useRef` instead of `useState` for `wsRef`?**
`useRef` stores a value without causing a re-render when it changes. The WebSocket object itself doesn't need to trigger UI updates — only `status` does.

**Why `useCallback` for `connect` and `send`?**
Prevents these functions from being recreated on every render, which would cause the `useEffect` that calls `connect()` to run in an infinite loop.

---

## Deep Dive: index.css — Remote Shell Design

### Remote Shell

```css
.remote-shell {
    background: linear-gradient(160deg, #1c1c2e 0%, #12121a 60%, #0a0a14 100%);
    border: 2px solid #2d2d44;
    border-radius: 2rem;
    box-shadow:
        0 0 60px rgba(0, 212, 255, 0.08),
        0 40px 80px rgba(0, 0, 0, 0.8),
        inset 0 1px 0 rgba(255,255,255,0.05);
}
```

The three-layer box-shadow creates:
- A subtle cyan glow around the edges
- A deep drop shadow beneath
- A thin highlight on the top edge (mimics a physical device)

### LCD Panel

```css
.lcd-panel {
    background: linear-gradient(160deg, #0a1628 0%, #0d1f3c 100%);
    border: 1px solid rgba(0, 212, 255, 0.25);
    box-shadow:
        inset 0 2px 12px rgba(0,0,0,0.6),
        0 0 20px rgba(0, 212, 255, 0.1);
}
.lcd-text {
    color: #00d4ff;
    text-shadow: 0 0 10px rgba(0, 212, 255, 0.6), 0 0 20px rgba(0, 212, 255, 0.3);
}
```

The inset shadow makes the LCD look recessed. The text glow effect mimics a real LCD backlight.

### Button Styles

```css
.btn-num:active, .btn-num.pressed {
    transform: translateY(1px) scale(0.96);
    box-shadow: 0 2px 4px rgba(0,0,0,0.5), inset 0 2px 6px rgba(0,0,0,0.4);
    background: linear-gradient(145deg, #1a1a28, #242436);
}
```

The `.pressed` class moves the button 1px down and scales it slightly — simulating a physical button being pressed.

### Connection Status Dot

```css
.conn-dot.connected  { background: #22c55e; animation: blink 1.5s infinite; }
.conn-dot.connecting { background: #f59e0b; animation: pulse 1s infinite; }
.conn-dot.disconnected { background: #ef4444; }

@keyframes blink {
    0%,100% { opacity: 1; }
    50%     { opacity: 0.3; }
}
```

Green blinking = connected. Yellow pulsing = connecting. Solid red = disconnected.

---

## Deep Dive: PWA Files

### manifest.json

```json
{
    "name": "TV Broadcast Remote",
    "short_name": "TV Remote",
    "start_url": "/",
    "display": "standalone",
    "background_color": "#08080f",
    "theme_color": "#00d4ff",
    "icons": [
        { "src": "/favicon.svg", "sizes": "any", "type": "image/svg+xml" }
    ]
}
```

- `display: "standalone"` — hides browser chrome (URL bar, back button) when installed, making it look like a native app
- `theme_color` — the cyan color appears in the Android status bar
- This file is what triggers the "Add to Home Screen" prompt on mobile browsers

### sw.js (Service Worker)

```javascript
self.addEventListener('install', (event) => {
    event.waitUntil(
        caches.open('tv-remote-v1').then(cache =>
            cache.addAll(['/', '/src/App.jsx', '/src/index.css'])
        )
    );
});

self.addEventListener('fetch', (event) => {
    event.respondWith(
        caches.match(event.request).then(response =>
            response || fetch(event.request)
        )
    );
});
```

The service worker runs in the background and caches the app's files. If the user opens the remote while offline (after installing it), they still see the UI — it just won't connect until the network is back.

### vite.config.js — Dev Server Proxy

```javascript
export default defineConfig({
    plugins: [react()],
    server: {
        port: 5173,
        proxy: {
            '/api': {
                target: 'http://localhost:8085',
                changeOrigin: true,
                rewrite: path => path.replace(/^\/api/, '/tv-simulator/api')
            }
        }
    }
})
```

During development, when the React app calls `/api/channels`, Vite intercepts it and forwards it to `http://localhost:8085/tv-simulator/api/channels`. This avoids CORS errors in development without changing any backend code.

---

## The Remote UI Layout (Top to Bottom)

```
┌──────────────────────────────────┐
│  📡 TV Broadcast    [ℹ] [⚙]      │  ← header + settings/info buttons
├──────────────────────────────────┤
│  ● connected            21:45:03 │  ← connection status + live clock
│                                  │
│  ┌──────── LCD PANEL ──────────┐ │
│  │ TARGET: [📺 TV #1 ▼]        │ │  ← target selector dropdown
│  │                             │ │
│  │ CHANNEL        VOLUME       │ │
│  │   04           60           │ │  ← big channel/volume readout
│  │ DocuWorld      [ON]         │ │
│  │ Documentary                 │ │
│  │ ████████░░ 60/100           │ │  ← volume progress bar
│  │ 🎯 TV #1 – Living Room      │ │  ← target label
│  └─────────────────────────────┘ │
│                                  │
│  [⏻ POWER]   TARGET: #1  [🔊 MUTE] │  ← power + mute
│                                  │
│      CH          VOL             │
│    [ ▲ ]       [ 🔊+ ]           │  ← CH/VOL controls
│    [ ▼ ]       [ 🔉- ]           │
│                                  │
│  ──────── KEYPAD ────────        │
│  [ 1 ] [ 2 ] [ 3 ]              │
│  [ 4 ] [ 5 ] [ 6 ]              │  ← numeric keypad
│  [ 7 ] [ 8 ] [ 9 ]              │
│  [CLR] [ 0 ] [OK]               │
│                                  │
│  [📢 BROADCAST ALL]  [↺]         │  ← broadcast + refresh
│                                  │
│  TV·SIM              v1.0.0      │  ← brand strip
└──────────────────────────────────┘
```

---

## What to Say During Presentation

> "I built the PWA remote control — the interface that lets you control all the TVs. It's built with React and runs as a Progressive Web App, which means you can install it on your phone from the browser, no app store needed. The remote connects to the server via WebSocket and shows a live LCD display of whichever TV you're targeting. You can type channel numbers like a real remote — press 2 then 4 and it waits 1.5 seconds before committing to channel 24. You can also broadcast a command to all TVs at once using the BROADCAST ALL button. I also built the WebSocket hook that auto-reconnects if the server goes down."

---

## Key Concepts to Know

- **React** — JavaScript library for building UIs with components and state
- **useState** — React hook that stores and updates UI state
- **useEffect** — React hook for side effects (WebSocket setup, keyboard listeners, timers)
- **useCallback** — prevents functions from being recreated on every render
- **useRef** — stores a value without causing re-renders (used for WebSocket object, timers)
- **Custom Hook** — a reusable function (`useWebSocket`) that encapsulates related logic
- **PWA (Progressive Web App)** — a web app that can be installed like a native app
- **Service Worker** — background script that enables offline functionality and caching
- **manifest.json** — tells the browser how to install the app
- **Vite** — fast frontend build tool with hot module replacement
- **Tailwind CSS** — utility-first CSS framework (classes like `text-xs`, `rounded-xl`, `bg-primary`)
- **Digit accumulation** — buffering keypad input with a timeout before committing
- **EMA (Exponential Moving Average)** — smoothing technique used in clock calibration
