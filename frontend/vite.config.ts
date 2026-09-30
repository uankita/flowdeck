import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'node:path'

export default defineConfig({
  plugins: [react()],

  // The monorepo keeps a single .env at the repo root, one level up.
  // Only VITE_-prefixed variables are exposed to client code.
  envDir: path.resolve(__dirname, '..'),

  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },

  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      // Proxying in dev keeps the browser on a single origin, so CORS and
      // cookie behaviour match production.
      '/api': {
        target: process.env.VITE_API_BASE_URL ?? 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: process.env.VITE_API_BASE_URL ?? 'http://localhost:8080',
        ws: true,
        changeOrigin: true,
      },
    },
  },

  build: {
    outDir: 'dist',
    sourcemap: true,
  },
})
