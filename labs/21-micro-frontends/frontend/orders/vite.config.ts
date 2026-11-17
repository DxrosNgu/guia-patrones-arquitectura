import { federation } from '@module-federation/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import { shared } from '../platform/federation.shared'

// El remoto del equipo PEDIDOS. Expone dos piezas: la página "Mis pedidos" y el contador del carrito,
// que el shell pone en su barra. Las dos comparten el mismo estado del carrito (es el mismo remoto).
export default defineConfig({
  base: '/remotes/orders/',
  plugins: [
    react(),
    federation({
      name: 'orders',
      filename: 'remoteEntry.js',
      exposes: {
        './App': './src/App.tsx',
        './CartBadge': './src/CartBadge.tsx',
      },
      shared,
      // Los tipos de los remotos están escritos a mano en el shell (src/remotes.d.ts).
      dts: false,
    }),
  ],
  resolve: { dedupe: ['react', 'react-dom'] },
  build: { target: 'esnext' },
  server: { port: 5175, origin: 'http://localhost:5175' },
})
