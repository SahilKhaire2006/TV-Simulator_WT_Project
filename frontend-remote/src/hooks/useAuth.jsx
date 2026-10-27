/**
 * useAuth.js – authentication hook for the TV Broadcast Simulator.
 *
 * Manages login/register/logout, persists the token in localStorage,
 * and exposes the current user to all components.
 */
import { useState, useCallback, useEffect, createContext, useContext } from 'react'
import { api, getToken, setToken } from '../api/client.js'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser]         = useState(null)
  const [loading, setLoading]   = useState(true)
  const [error, setError]       = useState(null)

  // On localhost, clear any stale api_base that would bypass the Vite proxy
  useEffect(() => {
    const isLocalDev = window.location.hostname === 'localhost' ||
                       window.location.hostname === '127.0.0.1'
    if (isLocalDev) {
      localStorage.removeItem('tv_api_base')
    }
  }, [])

  // Check if already logged in on mount
  useEffect(() => {
    const token = getToken()
    if (!token) {
      setLoading(false)
      return
    }
    api.me()
      .then(data => setUser(data.user))
      .catch(() => setToken(''))
      .finally(() => setLoading(false))
  }, [])

  const login = useCallback(async (username, password) => {
    setError(null)
    try {
      const data = await api.login(username, password)
      setToken(data.token)
      setUser(data.user)
      return data.user
    } catch (e) {
      setError(e.message)
      throw e
    }
  }, [])

  const register = useCallback(async (username, password) => {
    setError(null)
    try {
      const data = await api.register(username, password)
      setToken(data.token)
      setUser(data.user)
      return data.user
    } catch (e) {
      setError(e.message)
      throw e
    }
  }, [])

  const logout = useCallback(async () => {
    try { await api.logout() } catch { /* ignore */ }
    setToken('')
    setUser(null)
  }, [])

  return (
    <AuthContext.Provider value={{ user, loading, error, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
