import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// En modo desarrollo (`npm run dev`), Vite hace de proxy igual que nginx en el contenedor.
const service = (port: number) => ({
  target: `http://localhost:${port}`,
  rewrite: (path: string) => path.replace(/^\/api\/[a-z]+/, '/api'),
})

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api/orders': { target: 'http://localhost:8102' },
      '/api/inventory': service(8103),
      '/api/payments': service(8104),
      '/api/shipping': service(8105),
    },
  },
})
