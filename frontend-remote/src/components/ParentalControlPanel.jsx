/**
 * ParentalControlPanel.jsx – Lets the user lock/unlock channels and
 * set a 4-digit PIN to override the lock.
 *
 * When parental control is enabled, the TV client shows a PIN overlay
 * when trying to tune to a locked channel.
 */
import { useState, useEffect, useCallback } from 'react'
import { api } from '../api/client.js'
import { useAuth } from '../hooks/useAuth.jsx'
import toast from 'react-hot-toast'

const CHANNELS = [
  { number: 1, name: 'NovaStar News', genre: 'News' },
  { number: 2, name: 'Galaxy Cinema', genre: 'Movies' },
  { number: 3, name: 'SportsPulse HD', genre: 'Sports' },
  { number: 4, name: 'DocuWorld', genre: 'Documentary' },
  { number: 5, name: 'KidsZone', genre: 'Kids' },
  { number: 6, name: 'MusicBox', genre: 'Music' },
  { number: 7, name: 'TechVision', genre: 'Technology' },
  { number: 8, name: 'FoodieTV', genre: 'Lifestyle' },
  { number: 9, name: 'HealthFirst', genre: 'Health' },
  { number: 10, name: 'NightOwl Classic', genre: 'Classic' },
  { number: 11, name: 'Discovery Max', genre: 'Discovery' },
  { number: 12, name: 'Action Horizon', genre: 'Action' },
  { number: 13, name: 'Travel Planet', genre: 'Travel' },
  { number: 14, name: 'Science Quest', genre: 'Science' },
  { number: 15, name: 'Retro Toons', genre: 'Animation' },
  { number: 16, name: 'Beat Wave', genre: 'Music' },
  { number: 17, name: 'Cyber Space', genre: 'Gaming' },
  { number: 18, name: 'Prime Premiere', genre: 'Cinema' },
  { number: 19, name: 'Epic Wildlife', genre: 'Nature' },
  { number: 20, name: 'Speed Racing', genre: 'Motorsports' },
  { number: 21, name: 'Urban Beats', genre: 'Urban' },
  { number: 22, name: 'Comedy Central', genre: 'Comedy' },
  { number: 23, name: 'Anime Hub', genre: 'Anime' },
  { number: 24, name: 'Indie Shorts', genre: 'Indie' },
]

export default function ParentalControlPanel({ onLockedChannelsChange, onEnabledChange }) {
  const { user } = useAuth()
  const [enabled, setEnabled]           = useState(false)
  const [hasPin, setHasPin]             = useState(false)
  const [lockedChannels, setLocked]    = useState(new Set())
  const [loading, setLoading]           = useState(true)

  // PIN management
  const [showPinForm, setShowPinForm]   = useState(false)
  const [pin, setPin]                   = useState('')
  const [showPinVerify, setShowPinVerify] = useState(false)
  const [verifyPin, setVerifyPin]       = useState('')

  const fetchStatus = useCallback(async () => {
    if (!user) { setLoading(false); return }
    setLoading(true)
    try {
      const data = await api.getParentalStatus()
      setEnabled(data.enabled)
      setHasPin(data.hasPin)
      setLocked(new Set(data.lockedChannels || []))
      if (onLockedChannelsChange) onLockedChannelsChange(new Set(data.lockedChannels || []))
      if (onEnabledChange) onEnabledChange(data.enabled)
    } catch (e) {
      console.error('Parental status error:', e.message)
    } finally {
      setLoading(false)
    }
  }, [user])

  useEffect(() => { fetchStatus() }, [fetchStatus])

  // ── Actions ─────────────────────────────────────────────────────────────────

  const toggleEnabled = async () => {
    try {
      await api.setParentalEnabled(!enabled)
      setEnabled(!enabled)
      if (onEnabledChange) onEnabledChange(!enabled)
      toast.success(`Parental control ${!enabled ? 'enabled' : 'disabled'}`)
    } catch (e) {
      toast.error('Failed: ' + e.message)
    }
  }

  const toggleChannelLock = async (channelNumber) => {
    try {
      if (lockedChannels.has(channelNumber)) {
        await api.unlockChannel(channelNumber)
        const newSet = new Set(lockedChannels); newSet.delete(channelNumber)
        setLocked(newSet)
        if (onLockedChannelsChange) onLockedChannelsChange(newSet)
        toast.success(`CH ${channelNumber} unlocked`)
      } else {
        await api.lockChannel(channelNumber)
        const newSet = new Set(lockedChannels); newSet.add(channelNumber)
        setLocked(newSet)
        if (onLockedChannelsChange) onLockedChannelsChange(newSet)
        toast.success(`CH ${channelNumber} locked`)
      }
    } catch (e) {
      toast.error('Failed: ' + e.message)
    }
  }

  const savePin = async () => {
    if (!/^\d{4}$/.test(pin)) {
      toast.error('PIN must be exactly 4 digits')
      return
    }
    try {
      await api.setPin(pin)
      setHasPin(true)
      setShowPinForm(false)
      setPin('')
      toast.success('PIN updated')
    } catch (e) {
      toast.error('Failed: ' + e.message)
    }
  }

  const handleVerifyPin = async () => {
    try {
      await api.verifyPin(verifyPin)
      toast.success('PIN accepted')
      setShowPinVerify(false)
      setVerifyPin('')
    } catch (e) {
      toast.error('Invalid PIN')
    }
  }

  // ── Render ──────────────────────────────────────────────────────────────────

  if (!user) {
    return (
      <div className="bg-surface2 border border-tvgray rounded-xl p-4">
        <h2 className="font-orbitron text-primary text-xs mb-2 tracking-wider">🔒 PARENTAL CONTROL</h2>
        <p className="text-muted text-xs">Log in to configure parental control.</p>
      </div>
    )
  }

  if (loading) {
    return (
      <div className="bg-surface2 border border-tvgray rounded-xl p-4">
        <p className="text-muted text-xs">Loading...</p>
      </div>
    )
  }

  return (
    <div className="bg-surface2 border border-tvgray rounded-xl p-4">
      <h2 className="font-orbitron text-primary text-xs mb-3 tracking-wider">🔒 PARENTAL CONTROL</h2>

      {/* Enable / disable toggle */}
      <div className="flex items-center justify-between mb-4">
        <span className="text-xs text-white">Parental Control</span>
        <button
          onClick={toggleEnabled}
          className={`relative w-12 h-6 rounded-full transition-colors ${enabled ? 'bg-primary' : 'bg-tvgray'}`}
        >
          <span
            className={`absolute top-0.5 w-5 h-5 rounded-full bg-white transition-transform ${enabled ? 'left-6' : 'left-0.5'}`}
          />
        </button>
      </div>

      {enabled ? (
        <>
          {/* PIN management */}
          <div className="mb-4 border-b border-tvgray/50 pb-3">
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs text-muted">PIN Override</span>
              {hasPin && <span className="text-xs text-success">✓ PIN set</span>}
            </div>

            {!showPinForm ? (
              <button
                onClick={() => setShowPinForm(true)}
                className="text-xs text-primary hover:underline"
              >
                {hasPin ? 'Change PIN →' : 'Set PIN →'}
              </button>
            ) : (
              <div className="flex flex-col gap-2 mt-2">
                <input
                  type="password"
                  value={pin}
                  onChange={e => setPin(e.target.value.replace(/\D/g, '').slice(0, 4))}
                  placeholder="4-digit PIN"
                  className="bg-surface border border-tvgray rounded-lg px-3 py-2 text-sm text-white font-mono tracking-widest focus:outline-none focus:border-primary"
                  maxLength={4}
                />
                <div className="flex gap-2">
                  <button onClick={savePin} className="flex-1 py-1.5 rounded-lg bg-primary text-bg text-xs font-semibold">Save</button>
                  <button onClick={() => { setShowPinForm(false); setPin('') }} className="px-3 py-1.5 rounded-lg border border-tvgray text-muted text-xs">Cancel</button>
                </div>
              </div>
            )}
          </div>

          {/* Channel lock grid */}
          <div>
            <p className="text-xs text-muted mb-2">Tap to lock/unlock channels:</p>
            <div className="grid grid-cols-4 gap-1.5 max-h-40 overflow-y-auto">
              {CHANNELS.map(ch => {
                const isLocked = lockedChannels.has(ch.number)
                return (
                  <button
                    key={ch.number}
                    onClick={() => toggleChannelLock(ch.number)}
                    className={`flex flex-col items-center py-1.5 rounded-lg text-xs transition-all
                      ${isLocked
                        ? 'bg-danger/20 border border-danger/40 text-danger'
                        : 'bg-surface border border-tvgray text-muted hover:border-primary/30 hover:text-white'}`}
                    title={`${ch.name} (${ch.genre})`}
                  >
                    <span className="font-mono font-bold">{String(ch.number).padStart(2, '0')}</span>
                    <span className="text-[10px]">{isLocked ? '🔒' : ch.genre.slice(0, 4)}</span>
                  </button>
                )
              })}
            </div>
            {lockedChannels.size > 0 && (
              <p className="text-xs text-muted mt-2">{lockedChannels.size} channel(s) locked</p>
            )}
          </div>
        </>
      ) : (
        <p className="text-muted text-xs">Enable parental control to lock channels and set a PIN override.</p>
      )}
    </div>
  )
}
