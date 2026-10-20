# Member 2 — Live Broadcast Synchronization

## Your Role in the Project

You built the feature that makes this simulator feel like a **real TV broadcast**.

On a real TV, when 1000 people tune to the same channel, they all see the same scene at the same moment — not from the beginning of the video. Your job was to replicate this behaviour: any number of browser tabs opening the same channel should show the **exact same frame at the exact same time**, no matter when they opened or reconnected.

This is the most technically complex feature in the project.

---

## The Core Problem You Solved

Without your sync logic, this is what would happen:
- TV #1 opens CH 4 → video starts from 0:00
- TV #2 opens CH 4 five minutes later → video also starts from 0:00
- Result: TV #1 is at 5:00, TV #2 is at 0:00 — completely out of sync

With your sync logic:
- TV #1 opens CH 4 → video seeks to 3:27 (where the "broadcast" currently is)
- TV #2 opens CH 4 five minutes later → video seeks to 8:27
- Result: both TVs are always at the same position ✓

---

## Files You Own

| File | Location | What it does |
|------|----------|--------------|
| `client-player.jsp` | `backend/src/main/webapp/` | All sync logic, video loading, HLS player |
| `TVServerEndpoint.java` | `backend/src/main/java/com/simulator/websocket/` | `BROADCAST_EPOCH` constant, sets `broadcastEpoch` in messages |
| `WsMessage.java` | `backend/src/main/java/com/simulator/model/` | `serverTime` and `broadcastEpoch` fields |
| `Channel.java` | `backend/src/main/java/com/simulator/model/` | Channel data model with `streamUrl` |

---

## The Synchronization Formula

### Concept: Wall-Clock Broadcasting

Think of each channel's video as a **looping tape** playing on a broadcast tower since the server started.
Any viewer who tunes in calculates: "how far into the loop is the tape right now?" and seeks to that position.

```
position = (time_elapsed_since_server_start + channel_phase_offset) % video_duration
```

### Why a Phase Offset?

Each channel gets a different starting offset (73 seconds × channel number) so they don't all loop at the same time:
- CH 2: offset = 73s
- CH 3: offset = 146s
- CH 4: offset = 219s

This makes channels feel independent — they're not all restarting simultaneously.

---

## Deep Dive: BROADCAST_EPOCH (Server Side)

```java
// TVServerEndpoint.java
private static final long BROADCAST_EPOCH = System.currentTimeMillis();
```

This is a `static final` constant — it is set **once when the server starts** and never changes.
Think of it as "the moment the broadcast tower went on air."

Every `STATE_UPDATE` message sent to a TV includes this epoch:
```java
WsMessage reply = WsMessage.stateUpdate(cs, ch);
reply.setBroadcastEpoch(BROADCAST_EPOCH);  // attach the epoch
sendToSession(tvSession, GSON.toJson(reply));
```

Because every TV receives the same `broadcastEpoch`, they all use the same reference point to calculate their seek position — even if they connected at different times.

---

## Deep Dive: Server Clock Calibration (Client Side)

### The Problem with Client Clocks

Different browsers can have slightly different system clocks — one might be 2 seconds ahead, another 1 second behind. If each TV uses its own `Date.now()`, they'll calculate slightly different seek positions even with the same formula.

### The Solution: Clock Offset

When the TV receives a `STATE_UPDATE`, the message contains `msg.serverTime` — the server's `System.currentTimeMillis()` at the moment of sending.

```javascript
// client-player.jsp
let _clockOffset    = 0;
let _broadcastEpoch = 0;
let _clockCalibrated = false;

// On every STATE_UPDATE:
if (msg.serverTime && msg.serverTime > 0) {
    const rawOffset = msg.serverTime - Date.now();
    if (!_clockCalibrated) {
        _clockOffset = rawOffset;           // first message: set directly
    } else {
        // subsequent messages: smooth with Exponential Moving Average
        _clockOffset = Math.round(_clockOffset * 0.9 + rawOffset * 0.1);
    }
}
if (msg.broadcastEpoch && msg.broadcastEpoch > 0) {
    _broadcastEpoch  = msg.broadcastEpoch;
    _clockCalibrated = true;
}
```

**Exponential Moving Average (α=0.1)** smooths out one-off network jitter — a single slow message doesn't suddenly throw the sync off.

### serverNow()

```javascript
function serverNow() {
    return Date.now() + _clockOffset;
}
```

This function returns what the **server's clock reads right now**, from the client's perspective.
Every sync calculation uses `serverNow()` instead of `Date.now()`.

---

## Deep Dive: getLiveTarget()

```javascript
function getLiveTarget(ch, dur) {
    const elapsedSec = (serverNow() - _broadcastEpoch) / 1000;
    const shift      = (ch - 1) * 73;
    return ((elapsedSec + shift) % dur + dur) % dur;
}
```

Step by step:
1. `serverNow() - _broadcastEpoch` = milliseconds since server started
2. `/ 1000` = convert to seconds
3. `+ shift` = add this channel's phase offset
4. `% dur` = wrap around when the video loops
5. `+ dur) % dur` = ensure result is always positive (handles edge cases)

**Example:**
- Server started 500 seconds ago
- CH 4, phase shift = 3 × 73 = 219s
- Video duration = 120s
- `(500 + 219) % 120 = 719 % 120 = 119s` → seek to 1:59

Every TV running CH 4 will calculate exactly `119s` at this moment. ✓

---

## Deep Dive: loadStream()

This function handles loading the video for any channel number. It uses a **generation counter** to handle rapid channel switching without race conditions.

```javascript
let _loadGen = 0;

function loadStream(channelNumber) {
    const gen = ++_loadGen;  // increment generation

    // If loadStream is called again before this one finishes,
    // gen !== _loadGen will be true and all callbacks will exit early
    ...
    function startPlayback() {
        if (gen !== _loadGen) return;  // stale — a newer call took over
        ...
    }
}
```

### Three playback paths:

**Channels 2–24 (local MP4 files):**
```javascript
video.loop = true;
video.src  = ctxPath + '/videos/ch' + channelNumber + '.mp4';

const onMeta = () => {
    video.removeEventListener('loadedmetadata', onMeta);
    if (gen === _loadGen) {
        alignToLiveBroadcast(channelNumber);  // seek to correct position
        safePlay();
    }
};
video.addEventListener('loadedmetadata', onMeta);
```
Sync fires **only after `loadedmetadata`** — this guarantees `video.duration` is known before seeking.

**Channel 1 (HLS stream via HLS.js):**
```javascript
const hls = new Hls({ enableWorker: true, lowLatencyMode: true });
hls.loadSource('https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8');
hls.attachMedia(video);

hls.on(Hls.Events.MANIFEST_PARSED, () => {
    alignToLiveBroadcast(1);
    safePlay();
});

hls.on(Hls.Events.LEVEL_LOADED, (_, data) => {
    // Use HLS segment duration for sync
    const dur    = data.details.totalduration;
    const target = getLiveTarget(1, dur);
    video.currentTime = target;
});
```

**HLS.js** is a JavaScript library that plays HLS (HTTP Live Streaming) — a format used by real broadcasters like YouTube Live and Twitch. Your code integrates it and applies the same sync formula to the live stream.

**Channels 25+ (no video mapped):**
```javascript
video.src = sigLostUrl;  // shows signal_lost.mp4
```

---

## Deep Dive: alignToLiveBroadcast()

```javascript
function alignToLiveBroadcast(ch) {
    const dur = video.duration;
    if (!dur || isNaN(dur) || !isFinite(dur) || dur <= 0) return;

    let target;
    if (_clockCalibrated && _broadcastEpoch > 0) {
        target = getLiveTarget(ch, dur);       // use server clock
    } else {
        const nowSec = Date.now() / 1000;
        target = ((nowSec + (ch - 1) * 73) % dur + dur) % dur;  // fallback
    }

    if (Math.abs(video.currentTime - target) > 0.5) {
        video.currentTime = target;
    }
}
```

- First checks `video.duration` is valid — never seeks before the video is loaded
- Uses server clock when calibrated, falls back to local clock during initial load
- Only seeks if drift is more than 0.5 seconds — prevents unnecessary jumps

---

## Deep Dive: Drift Correction Interval

Even after initial sync, tiny drifts can accumulate over time. This interval continuously corrects them:

```javascript
setInterval(() => {
    if (!tvState.powered || video.paused || video.seeking) return;
    const dur = video.duration;
    if (!dur || isNaN(dur) || !isFinite(dur) || dur <= 0) return;

    const target = getLiveTarget(tvState.channel, dur);

    if (Math.abs(video.currentTime - target) > 0.5) {
        video.currentTime = target;
    }
}, 2000);
```

- Runs every **2 seconds** (was 4s before — you tightened it)
- Skips if `video.seeking` — avoids fighting its own seek
- Tolerance: **±0.5 seconds** (was ±2s before — you tightened it)
- Result: all TVs stay within half a second of each other at all times

---

## safePlay() — Handling Browser Autoplay Policies

Modern browsers block videos from autoplaying with sound. Your `safePlay()` handles this:

```javascript
function safePlay() {
    const p = video.play();
    if (p !== undefined) {
        p.catch(err => {
            if (err.name === 'NotAllowedError') {
                video.muted = true;   // mute and retry
                video.play().catch(() => {});
            }
        });
    }
}
```

If the browser blocks autoplay, it mutes the video and tries again. The user sees a "Click to enable sound" prompt.

---

## What to Say During Presentation

> "I built the live broadcast synchronization — the feature that makes this feel like a real TV channel. The challenge was: how do you make multiple browser tabs show the same frame at the same time when they connected at different moments? My solution uses a fixed server epoch — a timestamp set when the server starts — and a formula that calculates exactly where in the video loop the 'broadcast' currently is. Every TV independently runs this formula using the server's clock, so they all arrive at the same answer. I also handle HLS streaming for channel 1, and a drift correction that runs every 2 seconds to keep everyone within half a second of each other."

---

## Key Concepts to Know

- **HLS (HTTP Live Streaming)** — video streaming format used by real broadcasters; HLS.js plays it in browsers
- **Epoch** — a fixed reference timestamp; `BROADCAST_EPOCH` is the server's start time
- **Clock offset** — difference between server clock and client clock, used to correct for skew
- **Exponential Moving Average** — smoothing technique that reduces the impact of outliers
- **Generation counter** — prevents race conditions when rapidly switching channels
- **`loadedmetadata` event** — fires when the browser knows the video duration; safe to seek after this
- **Drift correction** — periodic check that re-syncs the video if it has drifted from the target
- **`video.seeking`** — true while the browser is processing a seek; skip correction during this
