import path from 'node:path'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(import.meta.dirname, './src'),
      // Usa `new Function` ao carregar (barrado pela CSP); só o textureCompress do gltf-transform precisa dele.
      'ndarray-pixels': path.resolve(import.meta.dirname, './src/features/architecture/convert/ndarray-pixels-stub.ts'),
    },
  },
  build: {
    // Fonte nunca vira data: URL (a CSP do nginx só aceita font-src 'self').
    assetsInlineLimit: (file) => (/\.(woff2?|ttf|otf)$/.test(file) ? false : undefined),
  },
  server: {
    port: 5173,
    host: true,
    proxy: {
      '/api': {
        target: process.env.API_PROXY_TARGET ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
