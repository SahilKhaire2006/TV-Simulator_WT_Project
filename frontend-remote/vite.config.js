import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    react(),
  ],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8085',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '/tv-simulator/api')
      },
      '/ws': {
        target: 'http://localhost:8085',
        changeOrigin: true,
        ws: true
      }
    }
  }
})
