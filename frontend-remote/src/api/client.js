/**
 * API client for the TV Broadcast Simulator backend.
 *
 * In development (Vite dev server), requests to /api/* are proxied to
 * the Java backend. In production (Vercel), API_BASE should point to the
 * deployed backend URL and is stored in localStorage so the user can
 * configure it from the settings panel.
 */

const DEFAULT_API_BASE = (() => {
  // Detect dev by hostname — localhost means Vite dev server, use proxy (empty base)
  const isLocalDev = window.location.hostname === 'localhost' ||
                     window.location.hostname === '127.0.0.1'
  if (isLocalDev) return ''
  // Production (Vercel or any other host): read from localStorage or use same-host backend
  const proto = window.location.protocol === 'https:' ? 'https:' : 'http:'
  return `${proto}//${window.location.hostname}:8085/tv-simulator`
})()

export function getApiBase() {
  // On localhost, always use '' so Vite proxy handles /api/* routing
  const isLocalDev = window.location.hostname === 'localhost' ||
                     window.location.hostname === '127.0.0.1'
  if (isLocalDev) return ''
  return localStorage.getItem('tv_api_base') || DEFAULT_API_BASE
}

export function setApiBase(url) {
  localStorage.setItem('tv_api_base', url)
}

/**
 * Get the auth token from localStorage.
 */
export function getToken() {
  return localStorage.getItem('tv_auth_token') || ''
}

export function setToken(token) {
  if (token) localStorage.setItem('tv_auth_token', token)
  else localStorage.removeItem('tv_auth_token')
}

/**
 * Make an authenticated API request.
 *
 * @param {string} path   – API path, e.g. '/api/favorites'
 * @param {object} opts   – fetch options (method, body, etc.)
 * @returns {Promise<object>} parsed JSON response
 */
export async function apiRequest(path, opts = {}) {
  const base = getApiBase()
  const url = base ? `${base}${path}` : path

  const headers = {
    'Content-Type': 'application/json',
    ...opts.headers,
  }

  const token = getToken()
  if (token) headers['X-Auth-Token'] = token

  const resp = await fetch(url, {
    ...opts,
    headers,
  })

  const text = await resp.text()
  let data
  try { data = text ? JSON.parse(text) : {} } catch { data = { raw: text } }

  if (!resp.ok) {
    const errMsg = data.error || `HTTP ${resp.status}`
    const err = new Error(errMsg)
    err.status = resp.status
    err.data = data
    throw err
  }

  return data
}

// ── Convenience methods ──────────────────────────────────────────────────────

export const api = {
  // Auth
  register: (username, password) =>
    apiRequest('/api/auth/register', { method: 'POST', body: JSON.stringify({ username, password }) }),
  login: (username, password) =>
    apiRequest('/api/auth/login', { method: 'POST', body: JSON.stringify({ username, password }) }),
  logout: () => apiRequest('/api/auth/logout', { method: 'POST' }),
  me: () => apiRequest('/api/auth/me'),

  // Favorites
  getFavorites: () => apiRequest('/api/favorites'),
  addFavorite: (channel) => apiRequest('/api/favorites', { method: 'POST', body: JSON.stringify({ channel }) }),
  removeFavorite: (channel) => apiRequest('/api/favorites', { method: 'DELETE', body: JSON.stringify({ channel }) }),
  checkFavorite: (channel) => apiRequest(`/api/favorites/check?channel=${channel}`),

  // Visit history
  recordVisit: (channel) => apiRequest('/api/visits', { method: 'POST', body: JSON.stringify({ channel }) }),
  getMyVisits: () => apiRequest('/api/visits'),
  getAggregateVisits: () => apiRequest('/api/visits/aggregate'),

  // Parental control
  getParentalStatus: () => apiRequest('/api/parental/status'),
  getLockedChannels: () => apiRequest('/api/parental/locked'),
  lockChannel: (channel) => apiRequest('/api/parental/lock', { method: 'POST', body: JSON.stringify({ channel }) }),
  unlockChannel: (channel) => apiRequest('/api/parental/unlock', { method: 'POST', body: JSON.stringify({ channel }) }),
  setPin: (pin) => apiRequest('/api/parental/pin', { method: 'POST', body: JSON.stringify({ pin }) }),
  verifyPin: (pin) => apiRequest('/api/parental/verify', { method: 'POST', body: JSON.stringify({ pin }) }),
  setParentalEnabled: (enabled) => apiRequest('/api/parental/enable', { method: 'POST', body: JSON.stringify({ enabled }) }),

  // Commercials
  getCommercials: (channel) => apiRequest(`/api/commercials${channel ? `?channel=${channel}` : ''}`),
  getAllCommercials: () => apiRequest('/api/commercials?all=true'),
  createCommercial: (ad) => apiRequest('/api/commercials', { method: 'POST', body: JSON.stringify(ad) }),
  updateCommercial: (ad) => apiRequest('/api/commercials', { method: 'PUT', body: JSON.stringify(ad) }),
  deleteCommercial: (id) => apiRequest(`/api/commercials?id=${id}`, { method: 'DELETE' }),
}
