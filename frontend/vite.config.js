import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The app always calls a relative "/api". In development Vite proxies that to
// the backend; in production nginx does the same. This keeps the frontend code
// free of any hard-coded backend host.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
