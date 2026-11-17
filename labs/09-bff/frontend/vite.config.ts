import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// En modo desarrollo (`npm run dev`), Vite hace de proxy igual que nginx en el contenedor.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/bff': 'http://localhost:8110',
      '/api/orders': 'http://localhost:8102',
      '/api/payments': 'http://localhost:8104',
      '/api/shipments': 'http://localhost:8105',
    },
  },
})
