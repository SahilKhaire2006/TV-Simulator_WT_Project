<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.simulator.websocket.TVServerEndpoint" %>
<%@ page import="com.simulator.model.Channel" %>
<%@ page import="java.util.List" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
    <title>TV Client – Virtual Broadcast Simulator</title>
    <meta name="description" content="TV Client player connected to the Virtual Television Broadcast Simulator"/>

    <!-- Google Fonts -->
    <link rel="preconnect" href="https://fonts.googleapis.com"/>
    <link href="https://fonts.googleapis.com/css2?family=Orbitron:wght@400;700;900&family=Inter:wght@300;400;600&display=swap" rel="stylesheet"/>

    <!-- HLS.js for adaptive streaming -->
    <script src="https://cdn.jsdelivr.net/npm/hls.js@latest/dist/hls.min.js"></script>

    <style>
        /* ── Reset & Base ─────────────────────────────────────────────────── */
        *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

        :root {
            --primary:    #00d4ff;
            --secondary:  #7c3aed;
            --bg:         #0a0a0f;
            --surface:    #12121a;
            --surface2:   #1c1c2e;
            --text:       #e2e8f0;
            --muted:      #64748b;
            --danger:     #ef4444;
            --success:    #22c55e;
            --glow:       0 0 20px rgba(0, 212, 255, 0.4);
            --font-mono:  'Orbitron', monospace;
            --font-body:  'Inter', sans-serif;
        }

        body {
            background: var(--bg);
            color: var(--text);
            font-family: var(--font-body);
            min-height: 100vh;
            display: flex;
            flex-direction: column;
            overflow: hidden;
        }

        /* ── TV Bezel ─────────────────────────────────────────────────────── */
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
            position: relative;
        }

        /* ── Screen area ─────────────────────────────────────────────────── */
        .screen-container {
            position: relative;
            flex: 1;
            background: #000;
            overflow: hidden;
        }

        /* CRT scanline overlay */
        .screen-container::after {
            content: '';
            position: absolute;
            inset: 0;
            background: repeating-linear-gradient(
                0deg,
                transparent,
                transparent 2px,
                rgba(0,0,0,0.05) 2px,
                rgba(0,0,0,0.05) 4px
            );
            pointer-events: none;
            z-index: 10;
        }

        #videoPlayer {
            width: 100%;
            height: 100%;
            object-fit: cover;
            display: block;
            transition: opacity 0.6s ease;
        }

        #videoPlayer.transitioning { opacity: 0; }

        /* ── Off-screen ──────────────────────────────────────────────────── */
        .off-screen {
            display: none;
            position: absolute;
            inset: 0;
            background: #000;
            align-items: center;
            justify-content: center;
            flex-direction: column;
            gap: 16px;
            z-index: 20;
        }
        .off-screen.visible { display: flex; }
        .off-screen .power-icon {
            font-size: 4rem;
            color: var(--muted);
            animation: pulse 2s infinite;
        }
        @keyframes pulse { 0%,100%{opacity:.4} 50%{opacity:1} }

        /* ── Channel banner overlay ──────────────────────────────────────── */
        .channel-banner {
            position: absolute;
            bottom: 70px;
            left: 20px;
            right: 20px;
            display: flex;
            align-items: center;
            gap: 14px;
            background: rgba(0,0,0,0.75);
            backdrop-filter: blur(12px);
            border: 1px solid rgba(0,212,255,0.3);
            border-radius: 10px;
            padding: 12px 18px;
            z-index: 15;
            opacity: 0;
            transform: translateY(20px);
            transition: opacity 0.4s ease, transform 0.4s ease;
        }
        .channel-banner.visible {
            opacity: 1;
            transform: translateY(0);
        }
        .channel-number {
            font-family: var(--font-mono);
            font-size: 2rem;
            font-weight: 900;
            color: var(--primary);
            text-shadow: var(--glow);
            min-width: 60px;
            text-align: center;
        }
        .channel-info { flex: 1; }
        .channel-name {
            font-family: var(--font-mono);
            font-size: 1.1rem;
            font-weight: 700;
            color: var(--text);
        }
        .channel-genre {
            font-size: 0.8rem;
            color: var(--muted);
            margin-top: 2px;
        }

        /* ── Status bar ──────────────────────────────────────────────────── */
        .status-bar {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 8px 16px;
            background: var(--surface);
            border-top: 1px solid #2d2d44;
            font-family: var(--font-mono);
            font-size: 0.75rem;
            gap: 12px;
            flex-wrap: wrap;
        }
        .status-item {
            display: flex;
            align-items: center;
            gap: 6px;
            color: var(--muted);
        }
        .status-item .dot {
            width: 8px;
            height: 8px;
            border-radius: 50%;
            background: var(--success);
            animation: blink 1.5s infinite;
        }
        .status-item .dot.offline { background: var(--danger); animation: none; }
        @keyframes blink { 0%,100%{opacity:1} 50%{opacity:0.3} }

        .status-val { color: var(--primary); }

        /* ── Volume indicator ───────────────────────────────────────────── */
        .volume-overlay {
            position: absolute;
            top: 20px;
            right: 20px;
            background: rgba(0,0,0,0.8);
            border: 1px solid rgba(0,212,255,0.4);
            border-radius: 8px;
            padding: 10px 14px;
            z-index: 15;
            opacity: 0;
            transition: opacity 0.3s ease;
            font-family: var(--font-mono);
            display: flex;
            align-items: center;
            gap: 10px;
        }
        .volume-overlay.visible { opacity: 1; }
        .volume-bar-track {
            width: 120px;
            height: 6px;
            background: #2d2d44;
            border-radius: 3px;
            overflow: hidden;
        }
        .volume-bar-fill {
            height: 100%;
            background: linear-gradient(90deg, var(--primary), var(--secondary));
            border-radius: 3px;
            transition: width 0.3s ease;
        }
        .volume-label {
            color: var(--primary);
            font-size: 0.8rem;
            min-width: 36px;
        }

        /* ── Mute badge ─────────────────────────────────────────────────── */
        .mute-badge {
            position: absolute;
            top: 20px;
            left: 20px;
            background: rgba(239,68,68,0.9);
            color: #fff;
            font-family: var(--font-mono);
            font-size: 0.75rem;
            padding: 6px 12px;
            border-radius: 6px;
            z-index: 16;
            display: none;
        }
        .mute-badge.visible { display: block; }

        /* ── Connection toast ───────────────────────────────────────────── */
        .toast {
            position: fixed;
            top: 16px;
            left: 50%;
            transform: translateX(-50%) translateY(-80px);
            background: var(--surface2);
            border: 1px solid var(--primary);
            color: var(--text);
            padding: 10px 24px;
            border-radius: 8px;
            font-size: 0.85rem;
            transition: transform 0.4s ease;
            z-index: 100;
            font-family: var(--font-mono);
            box-shadow: var(--glow);
        }
        .toast.show { transform: translateX(-50%) translateY(0); }

        /* ── Spinner ─────────────────────────────────────────────────────── */
        .spinner {
            position: absolute;
            inset: 0;
            display: flex;
            align-items: center;
            justify-content: center;
            background: rgba(0,0,0,0.7);
            z-index: 25;
        }
        .spinner::after {
            content: '';
            width: 48px;
            height: 48px;
            border: 4px solid rgba(0,212,255,0.2);
            border-top-color: var(--primary);
            border-radius: 50%;
            animation: spin 0.8s linear infinite;
        }
        @keyframes spin { to { transform: rotate(360deg); } }
        .spinner.hidden { display: none; }

        /* ── TV Header Badge ─────────────────────────────────────────────── */
        .tv-header-badge {
            position: absolute;
            top: 14px;
            right: 18px;
            z-index: 30;
            background: rgba(10, 10, 18, 0.82);
            backdrop-filter: blur(8px);
            border: 1px solid rgba(0, 212, 255, 0.35);
            border-radius: 8px;
            padding: 6px 14px;
            display: flex;
            align-items: center;
            gap: 8px;
            font-family: var(--font-mono);
            font-size: 0.8rem;
            color: var(--primary);
            box-shadow: 0 4px 20px rgba(0,0,0,0.6);
            letter-spacing: 0.5px;
        }
        .audio-prompt {
            position: absolute;
            top: 60px;
            left: 50%;
            transform: translateX(-50%);
            background: rgba(10, 10, 20, 0.9);
            border: 1px solid var(--primary);
            backdrop-filter: blur(10px);
            color: #fff;
            padding: 8px 20px;
            border-radius: 30px;
            font-family: var(--font-mono);
            font-size: 0.8rem;
            z-index: 35;
            cursor: pointer;
            box-shadow: var(--glow);
            display: none;
            align-items: center;
            gap: 8px;
            animation: pulsePrompt 2s infinite;
        }
        @keyframes pulsePrompt { 0%,100%{transform:translateX(-50%) scale(1)} 50%{transform:translateX(-50%) scale(1.03)} }
        .audio-prompt.visible { display: flex; }
    </style>
</head>
<body>

<!-- ── Toast notification ─────────────────────────────────────────────────── -->
<div class="toast" id="toast">Connected to broadcast server</div>

<!-- ── TV Bezel ──────────────────────────────────────────────────────────── -->
<div class="tv-bezel">

    <!-- Screen -->
    <div class="screen-container" id="screenContainer">

        <!-- Loading spinner -->
        <div class="spinner" id="spinner"></div>

        <!-- TV Header Badge (for multi-screen testing) -->
        <div class="tv-header-badge" id="tvHeaderBadge">
            <span class="badge-icon">📺</span>
            <span id="tvBadgeName">TV Screen</span>
            <span style="color:var(--muted)">|</span>
            <span style="color:var(--muted)">ID:</span>
            <span class="badge-id" id="tvBadgeId">...</span>
        </div>

        <!-- Audio unlock prompt (handles browser autoplay policies across tabs) -->
        <div class="audio-prompt" id="audioPrompt" onclick="unlockAudio()">
            <span>🔊</span> Click TV to enable sound
        </div>

        <!-- Main video player -->
        <video id="videoPlayer" autoplay muted playsinline preload="auto"
               poster="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 16 9'%3E%3Crect width='16' height='9' fill='%23000'/%3E%3C/svg%3E">
            Your browser does not support HTML5 video.
        </video>

        <!-- Off-screen overlay -->
        <div class="off-screen" id="offScreen">
            <div class="power-icon">⏻</div>
            <p style="font-family:var(--font-mono);color:var(--muted);font-size:0.9rem;">
                TV is powered off
            </p>
        </div>

        <!-- Channel banner -->
        <div class="channel-banner" id="channelBanner">
            <div class="channel-number" id="bannerChannelNum">01</div>
            <div class="channel-info">
                <div class="channel-name" id="bannerChannelName">NovaStar News</div>
                <div class="channel-genre" id="bannerChannelGenre">News • 24/7 Live</div>
            </div>
        </div>

        <!-- Volume overlay -->
        <div class="volume-overlay" id="volumeOverlay">
            <span style="color:var(--primary)">🔊</span>
            <div class="volume-bar-track">
                <div class="volume-bar-fill" id="volumeBarFill" style="width:50%"></div>
            </div>
            <span class="volume-label" id="volumeLabel">50</span>
        </div>

        <!-- Mute badge -->
        <div class="mute-badge" id="muteBadge">🔇 MUTED</div>

    </div><!-- /screen-container -->

    <!-- Status bar -->
    <div class="status-bar">
        <div class="status-item">
            <div class="dot" id="wsDot"></div>
            <span>WS</span>
            <span class="status-val" id="wsStatus">Connecting…</span>
        </div>
        <div class="status-item">
            <span>CLIENT ID</span>
            <span class="status-val" id="statusClientId">–</span>
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
            <span>GENRE</span>
            <span class="status-val" id="statusGenre">–</span>
        </div>
        <div class="status-item">
            <span id="statusTime"></span>
        </div>
    </div>

</div><!-- /tv-bezel -->

<!-- ── Channel catalog (injected by JSP) ─────────────────────────────────── -->
<%
    List<Channel> channels = TVServerEndpoint.getChannelCatalog();
    StringBuilder chJson = new StringBuilder("[");
    for (int i = 0; i < channels.size(); i++) {
        Channel ch = channels.get(i);
        chJson.append("{")
              .append("\"number\":").append(ch.getNumber()).append(",")
              .append("\"name\":\"").append(ch.getName().replace("\"","\\\"")).append("\",")
              .append("\"logoUrl\":\"").append(ch.getLogoUrl()).append("\",")
              .append("\"streamUrl\":\"").append(ch.getStreamUrl()).append("\",")
              .append("\"genre\":\"").append(ch.getGenre()).append("\",")
              .append("\"description\":\"").append(ch.getDescription().replace("\"","\\\"")).append("\"")
              .append("}");
        if (i < channels.size() - 1) chJson.append(",");
    }
    chJson.append("]");
%>

<script>
// ── Channel catalog from server ────────────────────────────────────────────
const CHANNEL_CATALOG = <%= chJson.toString() %>;

// ── WebSocket URL & Dynamic Device Label ──────────────────────────────────
const WS_URL = (() => {
    const proto = (location.protocol === 'https:') ? 'wss:' : 'ws:';
    const params = new URLSearchParams(window.location.search);
    let tvName = params.get('name') || params.get('tv');
    if (!tvName) {
        let stored = sessionStorage.getItem('tv_custom_label');
        if (!stored) {
            const presets = ['Living Room TV', 'Bedroom TV', 'Kitchen TV', 'Sports Bar TV', 'Lounge TV'];
            const randIdx = Math.floor(Math.random() * presets.length);
            stored = presets[randIdx] + ' #' + Math.floor(100 + Math.random() * 900);
            sessionStorage.setItem('tv_custom_label', stored);
        }
        tvName = stored;
    }
    const wsUrl = proto + '//' + location.host + '<%= request.getContextPath() %>' + '/ws/tv';
    return { url: wsUrl, label: tvName };
})();

// ── State ──────────────────────────────────────────────────────────────────
let ws            = null;
let reconnectTimer= null;
let bannerTimer   = null;
let volumeTimer   = null;
let currentHls    = null;
let tvState       = { channel: 1, volume: 50, powered: true, muted: false, clientId: null };

// ── Server-clock calibration ───────────────────────────────────────────────
// On first STATE_UPDATE the server sends:
//   msg.serverTime     → System.currentTimeMillis() at moment of send
//   msg.broadcastEpoch → fixed server-start epoch (same for every message)
//
// We compute clockOffset = msg.serverTime - Date.now()
// Then serverNow() ≈ server's current wall clock, eliminating client skew.
//
// getLiveTarget(ch, dur) gives the identical seek position every client
// independently calculates for the same channel — true broadcast sync.
let _clockOffset    = 0;      // ms difference: serverClock - localClock
let _broadcastEpoch = 0;      // fixed ms epoch from server (set once)
let _clockCalibrated = false;

function serverNow() {
    // Returns milliseconds on the server's clock
    return Date.now() + _clockOffset;
}

function getLiveTarget(ch, dur) {
    // How many seconds has the channel's broadcast been running since epoch?
    // Each channel gets a 73-second phase offset so they don't all start at 0.
    const elapsedSec = (serverNow() - _broadcastEpoch) / 1000;
    const shift      = (ch - 1) * 73;
    return ((elapsedSec + shift) % dur + dur) % dur;  // always positive
}

// ── DOM refs ───────────────────────────────────────────────────────────────
const video           = document.getElementById('videoPlayer');
const spinner         = document.getElementById('spinner');
const offScreen       = document.getElementById('offScreen');
const channelBanner   = document.getElementById('channelBanner');
const bannerChannelNum= document.getElementById('bannerChannelNum');
const bannerChannelName=document.getElementById('bannerChannelName');
const bannerChannelGenre=document.getElementById('bannerChannelGenre');
const volumeOverlay   = document.getElementById('volumeOverlay');
const volumeBarFill   = document.getElementById('volumeBarFill');
const volumeLabel     = document.getElementById('volumeLabel');
const muteBadge       = document.getElementById('muteBadge');
const audioPrompt     = document.getElementById('audioPrompt');
const wsDot           = document.getElementById('wsDot');
const wsStatus        = document.getElementById('wsStatus');

// ── Audio & Autoplay Policy Management ────────────────────────────────────
let _userInteracted   = false;

function unlockAudio() {
    _userInteracted = true;
    if (audioPrompt) audioPrompt.classList.remove('visible');
    video.muted  = tvState.muted;
    video.volume = tvState.muted ? 0 : Math.max(0, Math.min(1, tvState.volume / 100));
    if (tvState.powered && video.paused) {
        video.play().catch(() => {});
    }
}

document.addEventListener('click', unlockAudio);
document.addEventListener('keydown', unlockAudio);

function showAudioUnlockPrompt() {
    if (!_userInteracted && !tvState.muted) {
        if (audioPrompt) audioPrompt.classList.add('visible');
    }
}

video.addEventListener('pause', () => {
    // If TV is powered on, auto-resume if paused unexpectedly by browser policy
    if (tvState.powered && !offScreen.classList.contains('visible') && video.src && video.src !== window.location.href) {
        setTimeout(() => {
            if (tvState.powered && video.paused) {
                const p = video.play();
                if (p !== undefined) {
                    p.catch(err => {
                        if (err.name === 'NotAllowedError') {
                            video.muted = true;
                            video.play().catch(() => {});
                            showAudioUnlockPrompt();
                        }
                    });
                }
            }
        }, 50);
    }
});
const statusClientId  = document.getElementById('statusClientId');
const statusChannel   = document.getElementById('statusChannel');
const statusVolume    = document.getElementById('statusVolume');
const statusGenre     = document.getElementById('statusGenre');
const statusTime      = document.getElementById('statusTime');
const toast           = document.getElementById('toast');

// ── Clock ─────────────────────────────────────────────────────────────────
function updateClock() {
    statusTime.textContent = new Date().toLocaleTimeString();
}
setInterval(updateClock, 1000);
updateClock();

// ── Toast helper ──────────────────────────────────────────────────────────
function showToast(msg, duration = 2500) {
    toast.textContent = msg;
    toast.classList.add('show');
    setTimeout(() => toast.classList.remove('show'), duration);
}

// ── Channel lookup ────────────────────────────────────────────────────────
function getChannelInfo(num) {
    const found = CHANNEL_CATALOG.find(c => c.number === num);
    if (found) return found;
    return {
        number: num,
        name: 'Signal Lost',
        streamUrl: '<%= request.getContextPath() %>/videos/signal_lost.mp4',
        genre: 'No Signal',
        description: 'Channel ' + num + ' Unavailable - Signal Lost'
    };
}

// ── Video / HLS loading ───────────────────────────────────────────────────
// ROOT CAUSE FIX: hls.destroy() sets video.src='' internally, which queues
// an async error event. We keep video.onerror=null for 150ms (flush window)
// so that stale error fires harmlessly. A generation counter (_loadGen)
// cancels stale callbacks if loadStream is called again quickly.
let _loadGen = 0;

function loadStream(channelNumber) {
    const gen    = ++_loadGen;
    const hadHls = !!currentHls;

    // Destroy HLS (may queue async error on video element)
    if (currentHls) { currentHls.destroy(); currentHls = null; }

    // Clear handlers - video.onerror stays null during flush window
    // so any pending error from hls.destroy() fires harmlessly
    video.onerror   = null;
    video.oncanplay = null;
    video.pause();

    spinner.classList.remove('hidden');
    setTimeout(() => { if (gen === _loadGen) spinner.classList.add('hidden'); }, 1500);

    const ctxPath    = '<%= request.getContextPath() %>';
    const sigLostUrl = ctxPath + '/videos/signal_lost.mp4';

    function safePlay() {
        const p = video.play();
        if (p !== undefined) {
            p.catch(err => {
                if (err.name === 'NotAllowedError') {
                    // Autoplay was blocked because of unmuted state; mute and retry playback
                    video.muted = true;
                    video.play().catch(() => {});
                }
            });
        }
    }

    function startPlayback() {
        if (gen !== _loadGen) return;  // newer loadStream call took over

        const hideSpinner = () => {
            if (gen === _loadGen) spinner.classList.add('hidden');
        };
        video.addEventListener('loadeddata', hideSpinner, { once: true });
        video.addEventListener('playing', hideSpinner, { once: true });
        video.addEventListener('canplay', hideSpinner, { once: true });

        // ── Live broadcast seek target ─────────────────────────────────────
        // Uses server clock (serverNow) + fixed broadcastEpoch so every TV
        // independently calculates the SAME position for a given channel.
        // Falls back to Date.now() before the first STATE_UPDATE calibrates
        // the clock (covers the brief window at initial page load).
        function alignToLiveBroadcast(ch) {
            const dur = video.duration;
            if (!dur || isNaN(dur) || !isFinite(dur) || dur <= 0) return;
            // If not yet calibrated (no STATE_UPDATE received yet), use a
            // best-effort local-clock estimate so the video isn't stuck at 0.
            let target;
            if (_clockCalibrated && _broadcastEpoch > 0) {
                target = getLiveTarget(ch, dur);
            } else {
                const nowSec = Date.now() / 1000;
                target = ((nowSec + (ch - 1) * 73) % dur + dur) % dur;
            }
            try {
                // Only seek if the gap is non-trivial (>0.5 s) to avoid jitter
                if (Math.abs(video.currentTime - target) > 0.5) {
                    video.currentTime = target;
                }
            } catch (e) {}
        }

        if (channelNumber >= 2 && channelNumber <= 24) {
            // ── Local MP4 channels 2-23: Synchronized Live Broadcast ───
            video.loop = true;
            video.onerror = () => {
                if (gen !== _loadGen) return;
                video.onerror = null;
                video.loop = true;
                video.src  = sigLostUrl;
                safePlay();
            };

            // Sync only fires AFTER duration is known (loadedmetadata)
            const onMeta = () => {
                video.removeEventListener('loadedmetadata', onMeta);
                if (gen === _loadGen) {
                    alignToLiveBroadcast(channelNumber);
                    safePlay();
                }
            };
            video.addEventListener('loadedmetadata', onMeta);

            video.src = ctxPath + '/videos/ch' + channelNumber + '.mp4';
            // If metadata is already cached by the browser, fire immediately
            if (video.readyState >= 1) {
                alignToLiveBroadcast(channelNumber);
            }
            safePlay();

        } else if (channelNumber === 1) {
            // ── Channel 1: HLS stream (Synchronized Live Broadcast) ────
            if (Hls.isSupported()) {
                const hls = new Hls({ enableWorker: true, lowLatencyMode: true });
                currentHls = hls;
                hls.loadSource('https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8');
                hls.attachMedia(video);

                const onMeta = () => {
                    video.removeEventListener('loadedmetadata', onMeta);
                    if (gen === _loadGen) {
                        alignToLiveBroadcast(1);
                        safePlay();
                    }
                };
                video.addEventListener('loadedmetadata', onMeta);

                hls.on(Hls.Events.MANIFEST_PARSED, () => {
                    if (gen !== _loadGen) return;
                    video.loop = false;
                    if (video.duration && isFinite(video.duration)) {
                        alignToLiveBroadcast(1);
                    }
                    safePlay();
                });

                hls.on(Hls.Events.LEVEL_LOADED, (_, data) => {
                    if (gen !== _loadGen) return;
                    if (data && data.details && data.details.totalduration) {
                        const dur = data.details.totalduration;
                        const target = _clockCalibrated && _broadcastEpoch > 0
                            ? getLiveTarget(1, dur)
                            : ((serverNow() / 1000) % dur);
                        if (Math.abs(video.currentTime - target) > 0.5) {
                            try { video.currentTime = target; } catch (e) {}
                        }
                    }
                });

                video.addEventListener('ended', function onEnded() {
                    video.removeEventListener('ended', onEnded);
                    if (gen === _loadGen && tvState.channel === 1) {
                        alignToLiveBroadcast(1);
                        safePlay();
                    }
                });

                hls.on(Hls.Events.ERROR, (_, data) => {
                    if (!data.fatal || gen !== _loadGen) return;
                    hls.destroy(); currentHls = null;
                    video.loop = true; video.src = sigLostUrl;
                    safePlay();
                });
                setTimeout(() => {
                    if (gen === _loadGen && currentHls && video.readyState < 2) {
                        hls.destroy(); currentHls = null;
                        video.loop = true; video.src = sigLostUrl;
                        safePlay();
                    }
                }, 8000);
            } else {
                const onMeta = () => {
                    video.removeEventListener('loadedmetadata', onMeta);
                    if (gen === _loadGen) {
                        alignToLiveBroadcast(1);
                        safePlay();
                    }
                };
                video.addEventListener('loadedmetadata', onMeta);
                video.onerror = () => {
                    if (gen !== _loadGen) return;
                    video.onerror = null; video.loop = true;
                    video.src = sigLostUrl; safePlay();
                };
                video.src = 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8';
                safePlay();
            }

        } else {
            // ── Channels 24-99: signal_lost immediately ─────────────────
            video.loop = true;
            const onMeta = () => {
                video.removeEventListener('loadedmetadata', onMeta);
                if (gen === _loadGen) {
                    alignToLiveBroadcast(channelNumber);
                    safePlay();
                }
            };
            video.addEventListener('loadedmetadata', onMeta);

            video.src  = sigLostUrl;
            if (video.readyState >= 1) {
                alignToLiveBroadcast(channelNumber);
            }
            safePlay();
        }
    }

    // Flush window: wait for HLS async errors to fire harmlessly,
    // THEN set up the new stream. No delay needed if no HLS was active.
    setTimeout(startPlayback, hadHls ? 150 : 0);
}

// ── Continuous live broadcast sync – runs every 2 s ──────────────────────
// Skips if the video is still seeking (avoid fighting our own seek).
// Tolerance tightened to ±0.5 s so all TVs stay within half a second.
setInterval(() => {
    if (!tvState.powered || video.paused || video.seeking) return;
    const dur = video.duration;
    if (!dur || isNaN(dur) || !isFinite(dur) || dur <= 0) return;
    const ch     = tvState.channel;
    const target = (_clockCalibrated && _broadcastEpoch > 0)
        ? getLiveTarget(ch, dur)
        : ((((Date.now() / 1000) + (ch - 1) * 73) % dur) + dur) % dur;
    if (Math.abs(video.currentTime - target) > 0.5) {
        try { video.currentTime = target; } catch (e) {}
    }
}, 2000);


// ── Apply new TV state from server ────────────────────────────────────────
function applyState(state) {
    const channelChanged = state.channel !== tvState.channel;
    const volumeChanged  = state.volume  !== tvState.volume;
    tvState = { ...tvState, ...state };

    // Power
    if (!tvState.powered) {
        offScreen.classList.add('visible');
        if (currentHls) { currentHls.destroy(); currentHls = null; }
        video.pause();
        video.src = '';
    } else {
        offScreen.classList.remove('visible');
        if (channelChanged || !video.src || video.src === window.location.href) {
            loadStream(tvState.channel);
            showChannelBanner(tvState.channel);
        }
    }

    // Volume
    const targetVol = Math.max(0, Math.min(1, tvState.volume / 100));
    video.volume = targetVol;
    if (_userInteracted) {
        video.muted = tvState.muted;
    } else {
        // Until user clicks TV screen, keep muted so Chrome autoplay policy doesn't force-pause
        video.muted = true;
        if (!tvState.muted) {
            showAudioUnlockPrompt();
        }
    }
    muteBadge.classList.toggle('visible', tvState.muted);

    if (volumeChanged || state.muted !== undefined) {
        showVolumeOverlay();
    }

    // CRITICAL: Ensure video NEVER stays paused when volume changes
    if (tvState.powered && video.paused && video.src && video.src !== window.location.href) {
        const p = video.play();
        if (p !== undefined) {
            p.catch(err => {
                if (err.name === 'NotAllowedError') {
                    video.muted = true;
                    video.play().catch(() => {});
                    showAudioUnlockPrompt();
                }
            });
        }
    }

    // Status bar
    statusClientId.textContent = tvState.clientId ?? '–';
    statusChannel.textContent  = tvState.channel;
    statusVolume.textContent   = tvState.volume;
    const chInfo = getChannelInfo(tvState.channel);
    statusGenre.textContent    = chInfo.genre;

    // Header Badge update
    const badgeName = document.getElementById('tvBadgeName');
    const badgeId   = document.getElementById('tvBadgeId');
    if (badgeName) badgeName.textContent = WS_URL.label;
    if (badgeId)   badgeId.textContent   = tvState.clientId ? ('#' + tvState.clientId) : '...';

    // Update banner info
    bannerChannelNum.textContent  = String(tvState.channel).padStart(2, '0');
    bannerChannelName.textContent = chInfo.name;
    bannerChannelGenre.textContent= chInfo.genre + ' • ' + chInfo.description;
}

// ── Channel banner ────────────────────────────────────────────────────────
function showChannelBanner(ch) {
    const chInfo = getChannelInfo(ch);
    bannerChannelNum.textContent  = String(ch).padStart(2, '0');
    bannerChannelName.textContent = chInfo.name;
    bannerChannelGenre.textContent= chInfo.genre + ' • ' + chInfo.description;
    channelBanner.classList.add('visible');
    clearTimeout(bannerTimer);
    bannerTimer = setTimeout(() => channelBanner.classList.remove('visible'), 4000);
}

// ── Volume overlay ────────────────────────────────────────────────────────
function showVolumeOverlay() {
    const pct = tvState.muted ? 0 : tvState.volume;
    volumeBarFill.style.width = pct + '%';
    volumeLabel.textContent   = tvState.muted ? '🔇' : tvState.volume;
    volumeOverlay.classList.add('visible');
    clearTimeout(volumeTimer);
    volumeTimer = setTimeout(() => volumeOverlay.classList.remove('visible'), 2500);
}

// ── WebSocket ─────────────────────────────────────────────────────────────
function connect() {
    ws = new WebSocket(WS_URL.url);

    ws.onopen = () => {
        wsDot.classList.remove('offline');
        wsStatus.textContent = 'Connected';
        clearTimeout(reconnectTimer);
        showToast('📡 Connected to broadcast server');

        // Register as TV client
        ws.send(JSON.stringify({
            type:        'REGISTER',
            payload:     'TV',
            clientLabel: WS_URL.label
        }));
    };

    ws.onmessage = (evt) => {
        let msg;
        try { msg = JSON.parse(evt.data); } catch (e) { return; }

        switch (msg.type) {
            case 'HELLO':
                console.log('[WS] Server says hello');
                break;

            case 'STATE_UPDATE':
                if (msg.clientId) tvState.clientId = msg.clientId;
                statusClientId.textContent = msg.clientId ?? '–';

                // ── Server-clock calibration (runs on every STATE_UPDATE) ──
                // serverTime is System.currentTimeMillis() stamped at send time.
                // We smooth the offset over multiple samples to avoid one-shot
                // network jitter throwing the sync off.
                if (msg.serverTime && msg.serverTime > 0) {
                    const rawOffset = msg.serverTime - Date.now();
                    if (!_clockCalibrated) {
                        _clockOffset = rawOffset;
                    } else {
                        // Exponential moving average (α=0.1) for stability
                        _clockOffset = Math.round(_clockOffset * 0.9 + rawOffset * 0.1);
                    }
                }
                if (msg.broadcastEpoch && msg.broadcastEpoch > 0) {
                    _broadcastEpoch  = msg.broadcastEpoch;
                    _clockCalibrated = true;
                }

                applyState({
                    channel:  msg.channel,
                    volume:   msg.volume,
                    powered:  msg.powered,
                    muted:    msg.muted,
                    clientId: msg.clientId
                });
                break;

            case 'PONG':
                console.debug('[WS] Pong received');
                break;

            default:
                console.log('[WS] Unhandled message type:', msg.type);
        }
    };

    ws.onclose = () => {
        wsDot.classList.add('offline');
        wsStatus.textContent = 'Disconnected';
        showToast('⚠️ Connection lost – reconnecting…', 4000);
        reconnectTimer = setTimeout(connect, 4000);
    };

    ws.onerror = (err) => {
        console.error('[WS] Error:', err);
        ws.close();
    };
}

// ── Keep-alive ping ───────────────────────────────────────────────────────
setInterval(() => {
    if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'PING' }));
    }
}, 30000);

// ── Boot ──────────────────────────────────────────────────────────────────
window.addEventListener('DOMContentLoaded', () => {
    // Load initial channel
    const initial = getChannelInfo(1);
    loadStream(1, initial.streamUrl);
    showChannelBanner(1);
    connect();
});
</script>
</body>
</html>
