import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// En modo desarrollo (`npm run dev`), Vite hace lo mismo que nginx en el
// contenedor: reenvía /api/catalog y /api/inventory a cada servicio.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api/catalog': {
        target: 'http://localhost:8101',
        rewrite: (path: string) => path.replace(/^\/api\/catalog/, '/api'),
      },
      '/api/inventory': {
        target: 'http://localhost:8103',
        rewrite: (path: string) => path.replace(/^\/api\/inventory/, '/api'),
      },
    },
  },
})
