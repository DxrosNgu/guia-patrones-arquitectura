import { federation } from '@module-federation/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import { shared } from '../platform/federation.shared'

// El SHELL (host): la página que el usuario abre. Carga los remotos EN TIEMPO DE EJECUCIÓN desde
// /remotes/<nombre>/remoteEntry.js, que su nginx manda al contenedor de cada equipo. No los conoce al compilar:
// un remoto nuevo se despliega sin reconstruir el shell.
export default defineConfig({
  plugins: [
    react(),
    federation({
      name: 'shell',
      remotes: {
        catalog: { type: 'module', name: 'catalog', entry: '/remotes/catalog/remoteEntry.js', entryGlobalName: 'catalog', shareScope: 'default' },
        orders: { type: 'module', name: 'orders', entry: '/remotes/orders/remoteEntry.js', entryGlobalName: 'orders', shareScope: 'default' },
      },
      shared,
      // Los tipos de los remotos están escritos a mano en el shell (src/remotes.d.ts).
      dts: false,
    }),
  ],
  resolve: { dedupe: ['react', 'react-dom'] },
  build: { target: 'esnext' },
  server: {
    port: 5173,
    // En desarrollo, cada equipo corre su remoto con `npm run dev` y el shell los alcanza por estos proxies.
    proxy: {
      '/remotes/catalog': 'http://localhost:5174',
      '/remotes/orders': 'http://localhost:5175',
    },
  },
})
