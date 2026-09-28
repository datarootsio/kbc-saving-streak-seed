import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// This file is the one thing here that runs in Node rather than the browser, and the
// project carries no @types/node (nothing else needs it). Declaring the one global we
// read keeps `tsc --noEmit` — which the lab runs as a check — green without adding a
// dependency for two lines.
declare const process: { env: Record<string, string | undefined> }

// Every backend endpoint lives under /api, so the dev proxy is a single rule.
//
// Both the port and the proxy target can be overridden from the environment, so a second
// instance can be run beside one that already holds 8080/5173 (another checkout, another
// agent's lab). Unset — or set to something that is not a port — they are exactly the
// values this file always had. The target must follow the port: a dev server on another
// port proxying to the default 8080 would talk to whichever application happens to hold
// it, which may not even be this codebase.
const wantedPort = Number(process.env.VITE_PORT)
const port = Number.isInteger(wantedPort) && wantedPort > 0 ? wantedPort : 5173
const apiTarget = process.env.VITE_API_TARGET || 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  server: {
    port,
    proxy: {
      '/api': apiTarget,
    },
  },
})
