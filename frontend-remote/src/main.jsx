import { StrictMode } from 'react'
import { createRoot }  from 'react-dom/client'
import { Toaster }     from 'react-hot-toast'
import './index.css'
import App             from './App.jsx'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
    <Toaster
      position="top-center"
      toastOptions={{
        style: {
          background: '#1c1c2e',
          color:      '#e2e8f0',
          border:     '1px solid #2d2d44',
          fontFamily: "'Inter', sans-serif",
          fontSize:   '0.85rem',
        },
        success: { iconTheme: { primary: '#22c55e', secondary: '#0a0a0f' } },
        error:   { iconTheme: { primary: '#ef4444', secondary: '#0a0a0f' } },
      }}
    />
  </StrictMode>
)
