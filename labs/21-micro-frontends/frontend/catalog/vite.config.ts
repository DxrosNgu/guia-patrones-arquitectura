import { federation } from '@module-federation/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import { shared } from '../platform/federation.shared'

// El remoto del equipo CATÁLOGO. Expone su página entera ("./App"); el shell decide dónde ponerla.
export default defineConfig({
  // Se sirve detrás del shell en /remotes/catalog/: sus chunks se piden con ese prefijo.
  base: '/remotes/catalog/',
  plugins: [
    react(),
    federation({
      name: 'catalog',
      filename: 'remoteEntry.js',
      exposes: { './App': './src/App.tsx' },
      shared,
      // Los tipos de los remotos están escritos a mano en el shell (src/remotes.d.ts).
      dts: false,
    }),
  ],
  resolve: { dedupe: ['react', 'react-dom'] },
  build: { target: 'esnext' },
  // Modo independiente: `npm run dev` y abrir http://localhost:5174/remotes/catalog/ (sin el shell).
  server: { port: 5174, origin: 'http://localhost:5174' },
})
