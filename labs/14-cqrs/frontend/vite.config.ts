import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// En modo desarrollo (`npm run dev`), Vite hace de proxy igual que nginx en el contenedor.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api/orders': 'http://localhost:8102',
      '/api/history': 'http://localhost:8108',
    },
  },
})
