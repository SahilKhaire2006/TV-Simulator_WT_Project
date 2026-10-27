/**
 * AuthPanel.jsx – Login / Register panel shown when not logged in.
 * Also embedded as a collapsible section inside the main app.
 */
import { useState } from 'react'
import { useAuth } from '../hooks/useAuth.jsx'
import toast from 'react-hot-toast'

export default function AuthPanel({ compact = false }) {
  const { login, register } = useAuth()
  const [mode, setMode]       = useState('login')    // 'login' | 'register'
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!username.trim() || !password) {
      toast.error('Username and password required')
      return
    }
    try {
      if (mode === 'login') {
        await login(username.trim(), password)
        toast.success('Logged in!')
      } else {
        await register(username.trim(), password)
        toast.success('Account created!')
      }
    } catch (err) {
      toast.error(err.message)
    }
  }

  return (
    <div className={`bg-surface2 border border-tvgray rounded-xl p-4 ${compact ? '' : 'max-w-sm mx-auto'}`}>
      <h2 className="font-orbitron text-primary text-xs mb-3 tracking-wider">
        {mode === 'login' ? '🔑 LOGIN' : '📝 REGISTER'}
      </h2>
      <form onSubmit={handleSubmit} className="flex flex-col gap-3">
        <input
          value={username}
          onChange={e => setUsername(e.target.value)}
          placeholder="Username"
          className="bg-surface border border-tvgray rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-primary transition-colors"
        />
        <input
          type="password"
          value={password}
          onChange={e => setPassword(e.target.value)}
          placeholder="Password (min 3 chars)"
          className="bg-surface border border-tvgray rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-primary transition-colors"
        />
        <button
          type="submit"
          className="py-2 rounded-lg bg-primary text-bg font-semibold text-sm hover:opacity-90 transition-opacity"
        >
          {mode === 'login' ? 'Login' : 'Create Account'}
        </button>
      </form>
      <button
        onClick={() => setMode(mode === 'login' ? 'register' : 'login')}
        className="mt-3 text-xs text-muted hover:text-primary transition-colors w-full text-center"
      >
        {mode === 'login' ? 'Need an account? Register →' : 'Already have an account? Login →'}
      </button>
      <p className="mt-2 text-xs text-muted/60 text-center">
        Default admin: <span className="text-primary/80 font-mono">admin / admin123</span>
      </p>
    </div>
  )
}
