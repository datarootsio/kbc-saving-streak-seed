import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Every backend endpoint lives under /api, so the dev proxy is a single rule.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
