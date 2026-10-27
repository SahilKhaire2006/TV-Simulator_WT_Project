/**
 * FavoritesPanel.jsx – Shows the logged-in user's favourite channels.
 *
 * The user can:
 *   • View their favourites list
 *   • Jump to a favourite channel on the selected TV
 *   • Remove a channel from favourites
 *   • Add the currently-displayed channel to favourites
 *
 * All operations use the X-Auth-Token header for per-user session handling.
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

const getChannelInfo = (num) => CHANNELS.find(c => c.number === num) ?? { name: 'Channel ' + num, genre: '' }

export default function FavoritesPanel({ currentChannel, onTuneTo }) {
  const { user } = useAuth()
  const [favorites, setFavorites]       = useState([])
  const [loading, setLoading]           = useState(false)
  const [isCurrentFav, setIsCurrentFav] = useState(false)

  // Fetch favourites
  const fetchFavorites = useCallback(async () => {
    setLoading(true)
    try {
      const data = await api.getFavorites()
      setFavorites(Array.isArray(data) ? data : [])
    } catch (e) {
      console.error('Failed to load favorites:', e.message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { if (user) fetchFavorites() }, [user, fetchFavorites])

  // Check if current channel is a favourite
  useEffect(() => {
    if (!user || !currentChannel) return
    api.checkFavorite(currentChannel)
      .then(data => setIsCurrentFav(data.isFavorite))
      .catch(() => {})
  }, [user, currentChannel, favorites])

  // ── Actions ─────────────────────────────────────────────────────────────────

  const addCurrent = async () => {
    try {
      await api.addFavorite(currentChannel)
      toast.success(`CH ${currentChannel} added to favourites`)
      fetchFavorites()
      setIsCurrentFav(true)
    } catch (e) {
      toast.error('Failed to add favourite: ' + e.message)
    }
  }

  const removeFav = async (channelNumber) => {
    try {
      await api.removeFavorite(channelNumber)
      toast.success(`CH ${channelNumber} removed`)
      fetchFavorites()
      if (channelNumber === currentChannel) setIsCurrentFav(false)
    } catch (e) {
      toast.error('Failed to remove: ' + e.message)
    }
  }

  const tuneTo = (channelNumber) => {
    onTuneTo(channelNumber)
    toast(`📺 Tuning to CH ${String(channelNumber).padStart(2, '0')} ${getChannelInfo(channelNumber).name}`, { duration: 1500 })
  }

  // ── Render ──────────────────────────────────────────────────────────────────

  if (!user) {
    return (
      <div className="bg-surface2 border border-tvgray rounded-xl p-4">
        <h2 className="font-orbitron text-primary text-xs mb-2 tracking-wider">⭐ FAVOURITES</h2>
        <p className="text-muted text-xs">Log in to manage your favourite channels.</p>
      </div>
    )
  }

  return (
    <div className="bg-surface2 border border-tvgray rounded-xl p-4">
      <h2 className="font-orbitron text-primary text-xs mb-3 tracking-wider">
        ⭐ FAVOURITES {favorites.length > 0 && `(${favorites.length})`}
      </h2>

      {/* Add current channel */}
      {currentChannel && (
        <button
          onClick={addCurrent}
          disabled={isCurrentFav}
          className={`w-full py-2 mb-3 rounded-lg text-xs font-semibold transition-all
            ${isCurrentFav
              ? 'bg-primary/20 text-primary/60 cursor-default'
              : 'bg-primary text-bg hover:opacity-90'}`}
        >
          {isCurrentFav ? '✓ Current channel is in favourites' : `+ Add CH ${currentChannel} to favourites`}
        </button>
      )}

      {/* Favourites list */}
      {loading ? (
        <p className="text-muted text-xs">Loading...</p>
      ) : favorites.length === 0 ? (
        <p className="text-muted text-xs">No favourites yet. Tune to a channel and add it!</p>
      ) : (
        <div className="flex flex-col gap-1.5 max-h-48 overflow-y-auto">
          {favorites.map(fav => {
            const info = getChannelInfo(fav.channelNumber)
            return (
              <div
                key={fav.id}
                className="flex items-center justify-between bg-surface rounded-lg px-3 py-2 border border-white/5 hover:border-primary/30 transition-colors"
              >
                <button
                  onClick={() => tuneTo(fav.channelNumber)}
                  className="flex items-center gap-2 flex-1 text-left min-w-0"
                >
                  <span className="font-orbitron text-primary text-sm font-bold whitespace-nowrap">
                    {String(fav.channelNumber).padStart(2, '0')}
                  </span>
                  <div className="min-w-0">
                    <div className="text-xs text-white truncate">{info.name}</div>
                    <div className="text-muted text-xs truncate">{info.genre}</div>
                  </div>
                </button>
                <button
                  onClick={() => removeFav(fav.channelNumber)}
                  className="ml-2 w-6 h-6 rounded flex items-center justify-center text-danger/60 hover:text-danger hover:bg-danger/10 transition-colors text-xs"
                  title="Remove"
                >
                  ✕
                </button>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
