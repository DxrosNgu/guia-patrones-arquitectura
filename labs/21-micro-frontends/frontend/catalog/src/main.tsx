// MODO INDEPENDIENTE: el equipo de catálogo desarrolla y prueba su remoto sin el shell ni los demás equipos.
import { Notice } from '@mercado/platform'
import { on } from '@mercado/platform/events'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'

// Sin shell, nadie atiende los eventos: se muestran en la consola para ver qué se habría mandado.
on('mercado:add-to-cart', (item) => console.info('[sin shell] add-to-cart', item))
on('mercado:navigate', ({ to }) => {
  window.history.replaceState(null, '', to)
  window.dispatchEvent(new PopStateEvent('popstate'))
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <main style={{ padding: '1rem', maxWidth: 1100, margin: '0 auto' }}>
      <Notice>Catálogo en modo independiente: sin el shell ni el carrito.</Notice>
      <App />
    </main>
  </StrictMode>,
)
