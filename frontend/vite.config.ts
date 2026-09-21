import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Every backend endpoint lives under /api, so the dev proxy is a single rule.
export default defineConfig({
  // Relative asset URLs, so the built page works wherever it is mounted rather than only at "/".
  // Behind nginx that is "/", and behind code-server's port proxy it is "/proxy/80/"; an absolute
  // "/assets/..." would ask the proxy's own root for the bundle and be refused. api.ts resolves the
  // backend the same way, against document.baseURI, so the page and its API share one prefix.
  base: './',
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
