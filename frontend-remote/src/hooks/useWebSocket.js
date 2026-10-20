/**
 * useWebSocket.js
 * Custom React hook that manages the WebSocket connection to the
 * TV Broadcast Simulator backend, auto-reconnects on drop, and
 * dispatches incoming messages to the caller via onMessage callback.
 */
import { useEffect, useRef, useCallback, useState } from 'react'

/** @typedef {'connecting'|'connected'|'disconnected'} WsStatus */

const RECONNECT_DELAY_MS = 3500
const PING_INTERVAL_MS   = 25000

/**
 * @param {string}   url        – WebSocket URL
 * @param {function} onMessage  – callback(parsedObject) for incoming messages
 * @returns {{ status: WsStatus, send: function, ws: React.MutableRefObject }}
 */
export function useWebSocket(url, onMessage) {
  const wsRef          = useRef(null)
  const reconnectTimer = useRef(null)
  const pingTimer      = useRef(null)
  const onMessageRef   = useRef(onMessage)
  const [status, setStatus] = useState(/** @type {WsStatus} */ ('disconnected'))

  // Keep onMessage ref fresh without re-running effect
  useEffect(() => { onMessageRef.current = onMessage }, [onMessage])

  const connect = useCallback(() => {
    if (!url) return

    console.log('[WS] Connecting to', url)
    setStatus('connecting')

    const ws = new WebSocket(url)
    wsRef.current = ws

    ws.onopen = () => {
      console.log('[WS] Connected')
      setStatus('connected')
      clearTimeout(reconnectTimer.current)

      // Register as remote control
      ws.send(JSON.stringify({
        type:        'REGISTER',
        payload:     'REMOTE',
        clientLabel: 'PWA Remote Control'
      }))

      // Periodic keep-alive
      pingTimer.current = setInterval(() => {
        if (ws.readyState === WebSocket.OPEN) {
          ws.send(JSON.stringify({ type: 'PING' }))
        }
      }, PING_INTERVAL_MS)
    }

    ws.onmessage = (evt) => {
      try {
        const msg = JSON.parse(evt.data)
        onMessageRef.current?.(msg)
      } catch (e) {
        console.warn('[WS] Non-JSON message:', evt.data)
      }
    }

    ws.onclose = (evt) => {
      console.log('[WS] Closed:', evt.code, evt.reason)
      setStatus('disconnected')
      clearInterval(pingTimer.current)
      reconnectTimer.current = setTimeout(connect, RECONNECT_DELAY_MS)
    }

    ws.onerror = (err) => {
      console.error('[WS] Error:', err)
      ws.close()
    }
  }, [url])

  // Initial connect
  useEffect(() => {
    connect()
    return () => {
      clearTimeout(reconnectTimer.current)
      clearInterval(pingTimer.current)
      wsRef.current?.close()
    }
  }, [connect])

  const send = useCallback((data) => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(typeof data === 'string' ? data : JSON.stringify(data))
    } else {
      console.warn('[WS] Cannot send – socket not open')
    }
  }, [])

  return { status, send, ws: wsRef }
}
