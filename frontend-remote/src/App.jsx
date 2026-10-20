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
 */
import { useState, useCallback, useEffect, useRef } from 'react'
import toast from 'react-hot-toast'
import { useWebSocket } from './hooks/useWebSocket.js'

// ── Constants ──────────────────────────────────────────────────────────────
const DEFAULT_WS_URL  = `ws://${window.location.hostname}:8085/tv-simulator/ws/tv`
const DIGIT_TIMEOUT   = 1500   // ms to wait before committing typed digits as channel

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

// ── Helpers ────────────────────────────────────────────────────────────────
const getChannelInfo = (num) =>
  CHANNELS.find(c => c.number === num) ?? { name: 'Signal Lost', genre: 'No Signal' }

const padChannel = (num) => String(num).padStart(2, '0')

// ─────────────────────────────────────────────────────────────────────────────
// Component
// ─────────────────────────────────────────────────────────────────────────────
export default function App() {
  // WebSocket
  const [wsUrl, setWsUrl]       = useState(DEFAULT_WS_URL)
  const [wsUrlInput, setWsUrlInput] = useState(DEFAULT_WS_URL)
  const [showSettings, setShowSettings] = useState(false)

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

  // Clock tick
  useEffect(() => {
    const t = setInterval(() => setClock(new Date()), 1000)
    return () => clearInterval(t)
  }, [])

  // ── WebSocket message handler ─────────────────────────────────────────────
  const handleMessage = useCallback((msg) => {
    switch (msg.type) {
      case 'HELLO':
        toast('📡 Remote connected to broadcast server', { icon: '✅' })
        break

      case 'CLIENT_LIST':
        setClients(msg.clients ?? [])
        // Sync state map
        setClientStates(prev => {
          const next = { ...prev }
          ;(msg.clients ?? []).forEach(c => { next[c.clientId] = c })
          return next
        })
        // Auto-select first if none selected
        if (msg.clients?.length > 0 && targetId === -1) {
          // keep broadcast as default; user can change
        }
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
        // Also refresh clients list entry
        setClients(prev =>
          prev.map(c => c.clientId === msg.clientId
            ? { ...c, channel: msg.channel, volume: msg.volume, powered: msg.powered, muted: msg.muted }
            : c
          )
        )
        break

      case 'PONG':
        break  // silently handle keepalive

      case 'ERROR':
        toast.error('Server: ' + (msg.payload ?? 'Unknown error'))
        break

      default:
        console.log('[App] Unhandled WS message:', msg)
    }
  }, [targetId])

  const { status, send } = useWebSocket(wsUrl, handleMessage)

  // ── Command sender ────────────────────────────────────────────────────────
  const sendCommand = useCallback((command, extra = {}) => {
    send({
      type:     'COMMAND',
      command,
      clientId: targetId,
      ...extra,
    })
  }, [send, targetId])

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
        return next.length > 2 ? digit : next  // max 2 digits
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
    setShowSettings(false)
    toast.success('Settings saved – reconnecting…')
  }

  // ── Keyboard shortcut support ─────────────────────────────────────────────
  useEffect(() => {
    const handleKey = (e) => {
      if (showSettings || showInfo) return
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
  }, [handleDigit, pressButton, sendCommand, showSettings, showInfo])

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

      {/* ── Settings panel ───────────────────────────────────────────────── */}
      {showSettings && (
        <div className="w-full max-w-sm mb-4 bg-surface2 border border-tvgray rounded-xl p-4 animate-slide-up">
          <h2 className="font-orbitron text-primary text-xs mb-3 tracking-wider">SETTINGS</h2>
          <label className="block text-muted text-xs mb-1">WebSocket Server URL</label>
          <input
            value={wsUrlInput}
            onChange={e => setWsUrlInput(e.target.value)}
            className="w-full bg-surface border border-tvgray rounded-lg px-3 py-2 text-sm text-white font-mono focus:outline-none focus:border-primary transition-colors"
            placeholder="ws://localhost:8085/tv-simulator/ws/tv"
          />
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
          <span className="text-xs text-muted">v1.0.0</span>
        </div>

      </div>{/* /remote-shell */}

      {/* ── Digit buffer floating indicator ──────────────────────────────── */}
      {digitBuffer && (
        <div className="fixed top-6 left-1/2 -translate-x-1/2 bg-surface2 border border-primary rounded-xl px-6 py-3 font-orbitron text-primary text-2xl font-black shadow-glowLg animate-slide-up z-50">
          CH {digitBuffer.padStart(2, '0')}
        </div>
      )}

    </div>
  )
}
