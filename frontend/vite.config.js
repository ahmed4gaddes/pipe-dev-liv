import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // Force explicitement le JSX runtime automatique pour esbuild — sans ça, Vitest (contrairement
  // au serveur de dev/build normal) transforme parfois les .jsx en JSX "classic"
  // (React.createElement) alors qu'aucun fichier du projet n'importe React explicitement.
  esbuild: {
    jsx: 'automatic',
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      }
    }
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.js',
    globals: true,
  },
})
