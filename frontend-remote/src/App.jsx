/**
 * App.jsx – Virtual Television Broadcast Simulator
 * PWA Remote Control
 *
 * Features:
 *  • WebSocket connection to Java backend with auto-reconnect
 *  • LCD status display showing target TV info, channel, volume
 *  • Numeric keypad (0-9) with channel digit accumulation
 *  • Channel Up/Down, Volume Up/Down/Mute controls
 *  • Power On/Off toggle
 *  • Broadcast-to-all mode
 *  • Client selector dropdown with live state
 *  • Settings panel for WebSocket URL
 *  • ★ Authentication (login/register/logout)
 *  • ★ Favourites panel (per-user, via JDBC)
 *  • ★ Parental control (lock channels + PIN override)
 *  • ★ Visit history recording (feeds admin recommendations)
 */
import { useState, useCallback, useEffect, useRef } from 'react'
import toast from 'react-hot-toast'
import { useWebSocket } from './hooks/useWebSocket.js'
import { useAuth } from './hooks/useAuth.jsx'
import { api, getApiBase, setApiBase } from './api/client.js'
import AuthPanel from './components/AuthPanel.jsx'
import FavoritesPanel from './components/FavoritesPanel.jsx'
import ParentalControlPanel from './components/ParentalControlPanel.jsx'

// ── Constants ──────────────────────────────────────────────────────────────
const DEFAULT_WS_URL = (() => {
  const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  // In production (Vercel), user can configure the WS URL in settings.
  // Default points to the backend host on port 8085.
  return `${proto}//${window.location.hostname}:8085/tv-simulator/ws/tv`
})()
const DIGIT_TIMEOUT = 1500

// ── Channel definitions (mirrors backend catalog) ─────────────────────────
const CHANNELS = [
  { number: 1,  name: 'NovaStar News',    genre: 'News'        },
  { number: 2,  name: 'Galaxy Cinema',    genre: 'Movies'      },
  { number: 3,  name: 'SportsPulse HD',   genre: 'Sports'      },
  { number: 4,  name: 'DocuWorld',        genre: 'Documentary' },
  { number: 5,  name: 'KidsZone',         genre: 'Kids'        },
  { number: 6,  name: 'MusicBox',         genre: 'Music'       },
  { number: 7,  name: 'TechVision',       genre: 'Technology'  },
  { number: 8,  name: 'FoodieTV',         genre: 'Lifestyle'   },
  { number: 9,  name: 'HealthFirst',      genre: 'Health'      },
  { number: 10, name: 'NightOwl Classic', genre: 'Classic'     },
  { number: 11, name: 'Discovery Max',    genre: 'Discovery'   },
  { number: 12, name: 'Action Horizon',   genre: 'Action'      },
  { number: 13, name: 'Travel Planet',    genre: 'Travel'      },
  { number: 14, name: 'Science Quest',    genre: 'Science'     },
  { number: 15, name: 'Retro Toons',      genre: 'Animation'   },
  { number: 16, name: 'Beat Wave',        genre: 'Music'       },
  { number: 17, name: 'Cyber Space',      genre: 'Gaming'      },
  { number: 18, name: 'Prime Premiere',   genre: 'Cinema'      },
  { number: 19, name: 'Epic Wildlife',    genre: 'Nature'      },
  { number: 20, name: 'Speed Racing',     genre: 'Motorsports' },
  { number: 21, name: 'Urban Beats',      genre: 'Urban'       },
  { number: 22, name: 'Comedy Central',   genre: 'Comedy'      },
  { number: 23, name: 'Anime Hub',        genre: 'Anime'       },
  { number: 24, name: 'Indie Shorts',     genre: 'Indie'       },
]

const getChannelInfo = (num) =>
  CHANNELS.find(c => c.number === num) ?? { name: 'Signal Lost', genre: 'No Signal' }

const padChannel = (num) => String(num).padStart(2, '0')

// ─────────────────────────────────────────────────────────────────────────────
// Component
// ─────────────────────────────────────────────────────────────────────────────
export default function App() {
  // ── Auth ───────────────────────────────────────────────────────────────────
  const { user, logout } = useAuth()

  // ── WebSocket ──────────────────────────────────────────────────────────────
  const [wsUrl, setWsUrl]           = useState(DEFAULT_WS_URL)
  const [wsUrlInput, setWsUrlInput] = useState(DEFAULT_WS_URL)
  const [showSettings, setShowSettings] = useState(false)
  const [showFavorites, setShowFavorites] = useState(false)
  const [showParental, setShowParental] = useState(false)
  const [showAccount, setShowAccount]   = useState(false)

  // Connected TV clients list
  const [clients, setClients]   = useState([])

  // Target: -1 = broadcast ALL, >0 = specific client ID
  const [targetId, setTargetId] = useState(-1)

  // Per-client state mirror (keyed by clientId)
  const [clientStates, setClientStates] = useState({})

  // Digit accumulation for numeric channel entry
  const [digitBuffer, setDigitBuffer] = useState('')
  const digitTimer = useRef(null)

  // UI feedback: last pressed button
  const [pressedKey, setPressedKey] = useState(null)

  // Settings / info
  const [showInfo, setShowInfo] = useState(false)
  const [clock, setClock]       = useState(new Date())

  // Parental control state (cached from API)
  const [lockedChannels, setLockedChannels] = useState(new Set())
  const [parentalEnabled, setParentalEnabled] = useState(false)
  const [showPinPrompt, setShowPinPrompt]   = useState(false)
  const [pinInput, setPinInput]             = useState('')
  const [pendingChannel, setPendingChannel] = useState(null)

  // API base URL (for settings)
  const [apiBaseInput, setApiBaseInput] = useState(getApiBase())

  // Clock tick
  useEffect(() => {
    const t = setInterval(() => setClock(new Date()), 1000)
    return () => clearInterval(t)
  }, [])

  // ── Fetch parental control status when user logs in ────────────────────────
  useEffect(() => {
    if (!user) {
      setLockedChannels(new Set())
      setParentalEnabled(false)
      return
    }
    api.getParentalStatus()
      .then(data => {
        setParentalEnabled(data.enabled)
        setLockedChannels(new Set(data.lockedChannels || []))
      })
      .catch(() => {})
  }, [user])

  // ── WebSocket message handler ─────────────────────────────────────────────
  const handleMessage = useCallback((msg) => {
    switch (msg.type) {
      case 'HELLO':
        toast('📡 Remote connected to broadcast server', { icon: '✅' })
        break

      case 'CLIENT_LIST':
        setClients(msg.clients ?? [])
        setClientStates(prev => {
          const next = { ...prev }
          ;(msg.clients ?? []).forEach(c => { next[c.clientId] = c })
          return next
        })
        break

      case 'STATE_UPDATE':
        setClientStates(prev => ({
          ...prev,
          [msg.clientId]: {
            clientId:    msg.clientId,
            clientLabel: msg.clientLabel,
            channel:     msg.channel,
            volume:      msg.volume,
            powered:     msg.powered,
            muted:       msg.muted,
          }
        }))
        setClients(prev =>
          prev.map(c => c.clientId === msg.clientId
            ? { ...c, channel: msg.channel, volume: msg.volume, powered: msg.powered, muted: msg.muted }
            : c
          )
        )

        // ── Record visit history when channel changes ──────────────────────
        // Fire-and-forget; the backend handles user identification via token.
        api.recordVisit(msg.channel).catch(() => {})
        break

      case 'PONG':
        break

      case 'ERROR':
        toast.error('Server: ' + (msg.payload ?? 'Unknown error'))
        break

      default:
        console.log('[App] Unhandled WS message:', msg)
    }
  }, [])

  const { status, send } = useWebSocket(wsUrl, handleMessage)

  // ── Command sender with parental control check ──────────────────────────────
  const sendCommand = useCallback((command, extra = {}) => {
    // If user is logged in and parental control is enabled, check for locked channels
    if (user && parentalEnabled && command === 'CHANNEL_SET' && extra.channel != null) {
      if (lockedChannels.has(extra.channel)) {
        // Channel is locked – prompt for PIN
        setPendingChannel(extra.channel)
        setShowPinPrompt(true)
        setPinInput('')
        return
      }
    }
    send({
      type:     'COMMAND',
      command,
      clientId: targetId,
      ...extra,
    })
  }, [send, targetId, user, parentalEnabled, lockedChannels])

  // ── PIN verification for locked channel ─────────────────────────────────────
  const verifyPinAndTune = async () => {
    try {
      await api.verifyPin(pinInput)
      // PIN accepted – tune to the locked channel
      send({
        type:     'COMMAND',
        command:  'CHANNEL_SET',
        clientId: targetId,
        channel:  pendingChannel,
      })
      toast.success('PIN accepted – tuning to locked channel')
      setShowPinPrompt(false)
      setPinInput('')
      setPendingChannel(null)
    } catch (e) {
      toast.error('Invalid PIN')
    }
  }

  // ── Tune to channel (used by Favorites panel) ───────────────────────────────
  const tuneToChannel = useCallback((channel) => {
    if (user && parentalEnabled && lockedChannels.has(channel)) {
      setPendingChannel(channel)
      setShowPinPrompt(true)
      setPinInput('')
      return
    }
    send({ type: 'COMMAND', command: 'CHANNEL_SET', clientId: targetId, channel })
  }, [send, targetId, user, parentalEnabled, lockedChannels])

  // ── Button press visual feedback ──────────────────────────────────────────
  const pressButton = useCallback((key, fn) => {
    setPressedKey(key)
    setTimeout(() => setPressedKey(null), 200)
    fn()
  }, [])

  // ── Numeric digit → channel ───────────────────────────────────────────────
  const handleDigit = useCallback((digit) => {
    pressButton(digit, () => {
      setDigitBuffer(prev => {
        const next = prev + digit
        clearTimeout(digitTimer.current)
        digitTimer.current = setTimeout(() => {
          const ch = parseInt(next, 10)
          if (ch >= 1 && ch <= 99) {
            sendCommand('CHANNEL_SET', { channel: ch })
            const info = getChannelInfo(ch)
            toast(`📺 CH ${padChannel(ch)} ${info.name}`, { duration: 1500 })
          }
          setDigitBuffer('')
        }, DIGIT_TIMEOUT)
        return next.length > 2 ? digit : next
      })
    })
  }, [pressButton, sendCommand])

  // ── Current display state ─────────────────────────────────────────────────
  const currentState = targetId === -1
    ? null
    : (clientStates[targetId] ?? null)

  const displayChannel = currentState?.channel ?? '––'
  const displayVolume  = currentState?.volume  ?? '––'
  const displayPowered = currentState?.powered ?? true
  const displayMuted   = currentState?.muted   ?? false
  const chInfo         = currentState ? getChannelInfo(currentState.channel) : { name: '–', genre: '–' }
  const targetLabel    = targetId === -1
    ? `ALL TVs (${clients.length})`
    : (currentState?.clientLabel ?? `TV #${targetId}`)

  // ── Settings save ─────────────────────────────────────────────────────────
  const saveSettings = () => {
    setWsUrl(wsUrlInput.trim())
    // Only save API base in production (not localhost – Vite proxy handles it there)
    const isLocalDev = window.location.hostname === 'localhost' ||
                       window.location.hostname === '127.0.0.1'
    if (!isLocalDev) {
      setApiBase(apiBaseInput.trim())
    }
    setShowSettings(false)
    toast.success('Settings saved – reconnecting…')
  }

  // ── Keyboard shortcut support ─────────────────────────────────────────────
  useEffect(() => {
    const handleKey = (e) => {
      if (showSettings || showInfo || showPinPrompt) return
      if (e.key >= '0' && e.key <= '9') { handleDigit(e.key); return }
      switch (e.key) {
        case 'ArrowUp':   e.preventDefault(); pressButton('ch+', () => sendCommand('CHANNEL_UP'));   break
        case 'ArrowDown': e.preventDefault(); pressButton('ch-', () => sendCommand('CHANNEL_DOWN')); break
        case '+':         pressButton('v+',  () => sendCommand('VOLUME_UP'));   break
        case '-':         pressButton('v-',  () => sendCommand('VOLUME_DOWN')); break
        case 'm': case 'M': pressButton('mute',  () => sendCommand('MUTE_TOGGLE'));  break
        case 'p': case 'P': pressButton('power', () => sendCommand('POWER_TOGGLE')); break
      }
    }
    window.addEventListener('keydown', handleKey)
    return () => window.removeEventListener('keydown', handleKey)
  }, [handleDigit, pressButton, sendCommand, showSettings, showInfo, showPinPrompt])

  // ──────────────────────────────────────────────────────────────────────────
  // RENDER
  // ──────────────────────────────────────────────────────────────────────────
  return (
    <div className="min-h-screen bg-bg flex flex-col items-center justify-start py-6 px-4 select-none no-tap-highlight">

      {/* ── Page header ──────────────────────────────────────────────────── */}
      <header className="w-full max-w-sm mb-4 flex items-center justify-between">
        <div>
          <h1 className="font-orbitron text-primary text-sm font-bold tracking-widest uppercase">
            📡 TV Broadcast
          </h1>
          <p className="text-muted text-xs mt-0.5">Virtual Simulator Remote</p>
        </div>
        <div className="flex items-center gap-2">
          {/* Account / Auth button */}
          <button
            onClick={() => setShowAccount(!showAccount)}
            className={`w-8 h-8 rounded-lg border flex items-center justify-center text-sm transition-colors
              ${user
                ? 'border-success/40 text-success'
                : 'border-tvgray text-muted hover:text-primary hover:border-primary'}`}
            title={user ? `Logged in as ${user.username}` : 'Login / Register'}
          >
            {user ? '👤' : '🔒'}
          </button>
          <button
            onClick={() => setShowFavorites(!showFavorites)}
            className="w-8 h-8 rounded-lg border border-tvgray flex items-center justify-center text-muted hover:text-primary hover:border-primary transition-colors text-sm"
            title="Favourites"
          >⭐</button>
          <button
            onClick={() => setShowParental(!showParental)}
            className="w-8 h-8 rounded-lg border border-tvgray flex items-center justify-center text-muted hover:text-primary hover:border-primary transition-colors text-sm"
            title="Parental Control"
          >🛡</button>
          <button
            onClick={() => setShowInfo(!showInfo)}
            className="w-8 h-8 rounded-lg border border-tvgray flex items-center justify-center text-muted hover:text-primary hover:border-primary transition-colors text-sm"
            title="Info"
          >ℹ</button>
          <button
            onClick={() => setShowSettings(!showSettings)}
            className="w-8 h-8 rounded-lg border border-tvgray flex items-center justify-center text-muted hover:text-primary hover:border-primary transition-colors text-sm"
            title="Settings"
          >⚙</button>
        </div>
      </header>

      {/* ── Account panel (login/register or user info) ──────────────────── */}
      {showAccount && (
        <div className="w-full max-w-sm mb-4 animate-slide-up">
          {user ? (
            <div className="bg-surface2 border border-tvgray rounded-xl p-4">
              <h2 className="font-orbitron text-primary text-xs mb-3 tracking-wider">👤 ACCOUNT</h2>
              <div className="flex items-center justify-between mb-3">
                <div>
                  <div className="text-sm text-white font-semibold">{user.username}</div>
                  <div className="text-xs text-muted">{user.role === 'admin' ? 'Administrator' : 'User'}</div>
                </div>
                <span className={`text-xs px-2 py-0.5 rounded-full ${user.role === 'admin' ? 'bg-secondary/20 text-secondary' : 'bg-primary/20 text-primary'}`}>
                  {user.role}
                </span>
              </div>
              <button
                onClick={() => { logout(); setShowAccount(false); toast.success('Logged out') }}
                className="w-full py-2 rounded-lg border border-danger/40 text-danger text-sm hover:bg-danger/10 transition-colors"
              >
                Logout
              </button>
            </div>
          ) : (
            <AuthPanel />
          )}
        </div>
      )}

      {/* ── Settings panel ───────────────────────────────────────────────── */}
      {showSettings && (
        <div className="w-full max-w-sm mb-4 bg-surface2 border border-tvgray rounded-xl p-4 animate-slide-up">
          <h2 className="font-orbitron text-primary text-xs mb-3 tracking-wider">SETTINGS</h2>
          <label className="block text-muted text-xs mb-1">WebSocket Server URL</label>
          <input
            value={wsUrlInput}
            onChange={e => setWsUrlInput(e.target.value)}
            className="w-full bg-surface border border-tvgray rounded-lg px-3 py-2 text-sm text-white font-mono focus:outline-none focus:border-primary transition-colors mb-3"
            placeholder="ws://localhost:8085/tv-simulator/ws/tv"
          />
          <label className="block text-muted text-xs mb-1">API Base URL (backend host – production only)</label>
          <input
            value={apiBaseInput}
            onChange={e => setApiBaseInput(e.target.value)}
            className="w-full bg-surface border border-tvgray rounded-lg px-3 py-2 text-sm text-white font-mono focus:outline-none focus:border-primary transition-colors"
            placeholder="https://your-backend.com/tv-simulator"
          />
          <p className="text-muted text-xs mt-1">In dev mode the Vite proxy is used automatically. Set this only for production (Vercel).</p>
          <div className="flex gap-2 mt-3">
            <button
              onClick={saveSettings}
              className="flex-1 py-2 rounded-lg bg-primary text-bg font-semibold text-sm hover:opacity-90 transition-opacity"
            >Save & Reconnect</button>
            <button
              onClick={() => setShowSettings(false)}
              className="px-4 py-2 rounded-lg border border-tvgray text-muted text-sm hover:text-primary hover:border-primary transition-colors"
            >Cancel</button>
          </div>
        </div>
      )}

      {/* ── Favorites panel ─────────────────────────────────────────────── */}
      {showFavorites && (
        <div className="w-full max-w-sm mb-4 animate-slide-up">
          <FavoritesPanel
            currentChannel={currentState?.channel}
            onTuneTo={tuneToChannel}
          />
        </div>
      )}

      {/* ── Parental control panel ─────────────────────────────────────── */}
      {showParental && (
        <div className="w-full max-w-sm mb-4 animate-slide-up">
          <ParentalControlPanel
            onLockedChannelsChange={setLockedChannels}
            onEnabledChange={setParentalEnabled}
          />
        </div>
      )}

      {/* ── Info panel ───────────────────────────────────────────────────── */}
      {showInfo && (
        <div className="w-full max-w-sm mb-4 bg-surface2 border border-tvgray rounded-xl p-4 animate-slide-up">
          <h2 className="font-orbitron text-primary text-xs mb-3 tracking-wider">KEYBOARD SHORTCUTS</h2>
          <div className="grid grid-cols-2 gap-x-4 gap-y-1.5 text-xs text-muted">
            {[['0–9','Channel digits'],['↑ / ↓','Channel Up/Down'],['+','Volume Up'],['-','Volume Down'],['M','Mute toggle'],['P','Power toggle']].map(([k,v]) => (
              <div key={k} className="flex items-center gap-2">
                <kbd className="bg-surface border border-tvgray rounded px-1.5 py-0.5 text-primary font-mono text-xs">{k}</kbd>
                <span>{v}</span>
              </div>
            ))}
          </div>
          <div className="mt-3 pt-3 border-t border-tvgray/50">
            <h3 className="font-orbitron text-primary text-xs mb-2">FEATURES</h3>
            <ul className="text-xs text-muted space-y-1">
              <li>⭐ <strong>Favourites:</strong> Save channels per user (JDBC)</li>
              <li>🔒 <strong>Parental Control:</strong> Lock channels + PIN</li>
              <li>📊 <strong>Visit History:</strong> Tracks channel visits</li>
              <li>📺 <strong>Commercials:</strong> Ads from database</li>
            </ul>
          </div>
          <button onClick={() => setShowInfo(false)} className="mt-3 text-xs text-muted hover:text-primary transition-colors">Close</button>
        </div>
      )}

      {/* ── Remote control shell ─────────────────────────────────────────── */}
      <div className="remote-shell w-full max-w-sm p-5 flex flex-col gap-5">

        {/* ── Top strip: connection + clock ──────────────────────────────── */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className={`conn-dot ${status}`} />
            <span className="text-xs font-mono text-muted capitalize">{status}</span>
          </div>
          <span className="font-orbitron text-xs text-muted tabular-nums">
            {clock.toLocaleTimeString()}
          </span>
        </div>

        {/* ── LCD Display ────────────────────────────────────────────────── */}
        <div className="lcd-panel p-4 rounded-xl flex flex-col gap-3">

          {/* Target selector */}
          <div className="flex items-center gap-2">
            <span className="text-muted text-xs font-mono whitespace-nowrap">TARGET:</span>
            <select
              value={targetId}
              onChange={e => setTargetId(Number(e.target.value))}
              className="flex-1 bg-transparent border border-primary/30 rounded-lg px-2 py-1 text-primary font-orbitron text-xs focus:outline-none focus:border-primary cursor-pointer"
            >
              <option value={-1}>📢 BROADCAST ALL ({clients.length} TVs)</option>
              {clients.map(c => (
                <option key={c.clientId} value={c.clientId}>
                  📺 TV #{c.clientId} – {c.clientLabel ?? 'Unknown'}
                </option>
              ))}
            </select>
          </div>

          {/* Main LCD readout */}
          <div className="flex items-center justify-between">
            {/* Channel */}
            <div className="flex flex-col">
              <span className="text-muted text-xs font-mono">CHANNEL</span>
              <div className="lcd-text text-4xl font-black leading-none mt-1">
                {digitBuffer ? digitBuffer.padStart(2, '_') : (currentState ? padChannel(displayChannel) : '––')}
              </div>
              <div className="text-primary/70 text-xs font-mono mt-1 truncate max-w-[120px]">
                {chInfo.name}
              </div>
              <div className="text-muted text-xs">{chInfo.genre}</div>
            </div>

            {/* Volume + status */}
            <div className="flex flex-col items-end gap-2">
              <div className="flex flex-col items-end">
                <span className="text-muted text-xs font-mono">VOLUME</span>
                <span className="lcd-text text-2xl font-bold">
                  {displayMuted ? '🔇' : (currentState ? `${displayVolume}` : '––')}
                </span>
              </div>
              {/* Power indicator */}
              <div className={`flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-mono border
                ${displayPowered
                  ? 'bg-success/10 border-success/30 text-success'
                  : 'bg-danger/10 border-danger/30 text-danger'}`}>
                <span className={`w-1.5 h-1.5 rounded-full ${displayPowered ? 'bg-success' : 'bg-danger'}`} />
                {displayPowered ? 'ON' : 'OFF'}
              </div>
              {/* Mute indicator */}
              {displayMuted && (
                <div className="flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-mono border border-warn/40 bg-warn/10 text-warn">
                  🔇 MUTED
                </div>
              )}
            </div>
          </div>

          {/* Volume bar */}
          <div>
            <div className="flex justify-between items-center mb-1">
              <span className="text-muted text-xs font-mono">VOL</span>
              <span className="text-primary text-xs font-mono">{currentState ? displayVolume : '--'}/100</span>
            </div>
            <div className="h-1.5 bg-tvgray rounded-full overflow-hidden">
              <div
                className="h-full rounded-full transition-all duration-300"
                style={{
                  width: currentState ? `${displayMuted ? 0 : displayVolume}%` : '0%',
                  background: 'linear-gradient(90deg, #00d4ff, #7c3aed)'
                }}
              />
            </div>
          </div>

          {/* Target label */}
          <div className="text-muted text-xs font-mono truncate border-t border-white/5 pt-2">
            🎯 {targetLabel}
          </div>
        </div>

        {/* ── Power + Mute row ───────────────────────────────────────────── */}
        <div className="flex items-center justify-between px-2">
          <button
            id="btn-power"
            onClick={() => pressButton('power', () => sendCommand('POWER_TOGGLE'))}
            className={`btn-power w-14 h-14 ${displayPowered ? 'powered-on' : ''}`}
            title="Power Toggle (P)"
          >
            <span className="text-2xl">⏻</span>
          </button>

          <div className="flex flex-col items-center gap-1">
            <span className="text-muted text-xs font-mono">TARGET</span>
            <div className="text-primary text-xs font-orbitron text-center max-w-[120px] truncate">
              {targetId === -1 ? 'ALL' : `TV #${targetId}`}
            </div>
          </div>

          <button
            id="btn-mute"
            onClick={() => pressButton('mute', () => sendCommand('MUTE_TOGGLE'))}
            className={`btn-mute w-14 h-14 ${displayMuted ? 'muted' : ''}`}
            title="Mute Toggle (M)"
          >
            <span className="text-2xl">{displayMuted ? '🔇' : '🔊'}</span>
          </button>
        </div>

        {/* ── Channel + Volume controls ──────────────────────────────────── */}
        <div className="grid grid-cols-2 gap-3">
          {/* Channel controls */}
          <div className="flex flex-col gap-2">
            <span className="text-muted text-xs font-mono text-center tracking-widest">CH</span>
            <button
              id="btn-ch-up"
              onClick={() => pressButton('ch+', () => {
                sendCommand('CHANNEL_UP')
                toast('📺 Channel ▲', { duration: 1000 })
              })}
              className={`btn-ctrl h-14 text-xl ${pressedKey === 'ch+' ? 'scale-95' : ''}`}
            >
              <span>▲</span>
              <span className="text-xs">CH ▲</span>
            </button>
            <button
              id="btn-ch-down"
              onClick={() => pressButton('ch-', () => {
                sendCommand('CHANNEL_DOWN')
                toast('📺 Channel ▼', { duration: 1000 })
              })}
              className={`btn-ctrl h-14 text-xl ${pressedKey === 'ch-' ? 'scale-95' : ''}`}
            >
              <span>▼</span>
              <span className="text-xs">CH ▼</span>
            </button>
          </div>

          {/* Volume controls */}
          <div className="flex flex-col gap-2">
            <span className="text-muted text-xs font-mono text-center tracking-widest">VOL</span>
            <button
              id="btn-vol-up"
              onClick={() => pressButton('v+', () => sendCommand('VOLUME_UP'))}
              className={`btn-ctrl h-14 text-xl ${pressedKey === 'v+' ? 'scale-95' : ''}`}
            >
              <span>🔊+</span>
              <span className="text-xs">VOL ▲</span>
            </button>
            <button
              id="btn-vol-down"
              onClick={() => pressButton('v-', () => sendCommand('VOLUME_DOWN'))}
              className={`btn-ctrl h-14 text-xl ${pressedKey === 'v-' ? 'scale-95' : ''}`}
            >
              <span>🔉-</span>
              <span className="text-xs">VOL ▼</span>
            </button>
          </div>
        </div>

        {/* ── Divider ───────────────────────────────────────────────────── */}
        <div className="border-t border-tvgray/50 relative">
          <span className="absolute left-1/2 -translate-x-1/2 -top-2.5 bg-surface px-3 text-muted text-xs font-mono">
            KEYPAD
          </span>
        </div>

        {/* ── Numeric keypad 1–9, [CLEAR], 0, [⏎] ─────────────────────── */}
        <div className="grid grid-cols-3 gap-3">
          {[1,2,3,4,5,6,7,8,9].map(d => (
            <button
              key={d}
              id={`btn-num-${d}`}
              onClick={() => handleDigit(String(d))}
              className={`btn-num h-16 transition-all duration-100 ${pressedKey === String(d) ? 'pressed' : ''}`}
            >
              <span className="text-xl">{d}</span>
            </button>
          ))}

          {/* CLEAR */}
          <button
            id="btn-clear"
            onClick={() => pressButton('clear', () => {
              clearTimeout(digitTimer.current)
              setDigitBuffer('')
              toast('✖ Cleared', { duration: 800 })
            })}
            className={`btn-num h-16 !text-danger/70 hover:!text-danger text-sm ${pressedKey === 'clear' ? 'pressed' : ''}`}
          >
            CLR
          </button>

          {/* 0 */}
          <button
            id="btn-num-0"
            onClick={() => handleDigit('0')}
            className={`btn-num h-16 ${pressedKey === '0' ? 'pressed' : ''}`}
          >
            <span className="text-xl">0</span>
          </button>

          {/* Enter / OK */}
          <button
            id="btn-ok"
            onClick={() => pressButton('ok', () => {
              if (digitBuffer) {
                const ch = parseInt(digitBuffer, 10)
                clearTimeout(digitTimer.current)
                setDigitBuffer('')
                if (ch >= 1 && ch <= 99) {
                  sendCommand('CHANNEL_SET', { channel: ch })
                  const info = getChannelInfo(ch)
                  toast(`📺 CH ${padChannel(ch)} ${info.name}`, { duration: 1500 })
                } else {
                  toast.error(`Channel ${ch} out of range (1–99)`)
                }
              }
            })}
            className={`btn-num h-16 !text-primary text-sm font-bold ${pressedKey === 'ok' ? 'pressed' : ''}`}
          >
            OK
          </button>
        </div>

        {/* ── Broadcast controls ────────────────────────────────────────── */}
        <div className="flex gap-2">
          <button
            id="btn-broadcast-all"
            onClick={() => pressButton('bcast', () => {
              setTargetId(-1)
              toast('📢 Broadcasting to ALL TVs', { duration: 1500 })
            })}
            className={`flex-1 py-3 rounded-xl border text-xs font-orbitron transition-all
              ${targetId === -1
                ? 'bg-secondary/20 border-secondary text-secondary'
                : 'border-tvgray text-muted hover:border-secondary/50 hover:text-secondary'}`}
          >
            📢 BROADCAST ALL
          </button>
          <button
            id="btn-refresh-clients"
            onClick={() => pressButton('ref', () => {
              toast('🔄 Refreshing…', { duration: 1000 })
            })}
            className="px-4 py-3 rounded-xl border border-tvgray text-muted text-xs hover:border-primary/50 hover:text-primary transition-all"
          >
            ↺
          </button>
        </div>

        {/* ── Bottom brand strip ────────────────────────────────────────── */}
        <div className="border-t border-tvgray/30 pt-3 flex items-center justify-between">
          <span className="font-orbitron text-xs text-muted tracking-widest">TV·SIM</span>
          <span className="text-xs text-muted">v2.0.0</span>
        </div>

      </div>{/* /remote-shell */}

      {/* ── Digit buffer floating indicator ──────────────────────────────── */}
      {digitBuffer && (
        <div className="fixed top-6 left-1/2 -translate-x-1/2 bg-surface2 border border-primary rounded-xl px-6 py-3 font-orbitron text-primary text-2xl font-black shadow-glowLg animate-slide-up z-50">
          CH {digitBuffer.padStart(2, '0')}
        </div>
      )}

      {/* ── PIN prompt modal (parental control) ─────────────────────────────── */}
      {showPinPrompt && (
        <div className="fixed inset-0 bg-black/70 flex items-center justify-center z-50 px-4">
          <div className="bg-surface2 border border-primary/40 rounded-2xl p-6 max-w-xs w-full shadow-glowLg">
            <h2 className="font-orbitron text-primary text-sm mb-2 text-center">🔒 CHANNEL LOCKED</h2>
            <p className="text-muted text-xs text-center mb-4">
              CH {pendingChannel ? padChannel(pendingChannel) : ''} is locked.
              <br/>Enter your PIN to continue.
            </p>
            <input
              type="password"
              value={pinInput}
              onChange={e => setPinInput(e.target.value.replace(/\D/g, '').slice(0, 4))}
              onKeyDown={e => { if (e.key === 'Enter') verifyPinAndTune() }}
              placeholder="••••"
              className="w-full bg-surface border border-tvgray rounded-lg px-3 py-3 text-center text-white font-mono text-xl tracking-[0.5em] focus:outline-none focus:border-primary mb-3"
              maxLength={4}
              autoFocus
            />
            <div className="flex gap-2">
              <button
                onClick={verifyPinAndTune}
                className="flex-1 py-2 rounded-lg bg-primary text-bg font-semibold text-sm hover:opacity-90 transition-opacity"
              >Unlock</button>
              <button
                onClick={() => { setShowPinPrompt(false); setPinInput(''); setPendingChannel(null) }}
                className="px-4 py-2 rounded-lg border border-tvgray text-muted text-sm hover:text-white transition-colors"
              >Cancel</button>
            </div>
          </div>
        </div>
      )}

    </div>
  )
}
