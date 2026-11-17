// MODO INDEPENDIENTE: el equipo de pedidos prueba su remoto sin el shell ni el catálogo.
import { Button, Notice } from '@mercado/platform'
import { emit, on } from '@mercado/platform/events'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import CartBadge from './CartBadge'

on('mercado:navigate', ({ to }) => {
  window.history.replaceState(null, '', to)
  window.dispatchEvent(new PopStateEvent('popstate'))
})

// Sin catálogo, un botón emite el MISMO evento que emitiría él: así se prueba el contrato.
const fakeCatalog = () => emit('mercado:add-to-cart', { sku: 'INV-001', name: 'Paraguas solar', price: 49.9 })

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <main style={{ padding: '1rem', maxWidth: 1100, margin: '0 auto', display: 'grid', gap: '1rem' }}>
      <Notice>Mis pedidos en modo independiente. Carrito: <CartBadge /></Notice>
      <Button onClick={fakeCatalog}>Simular "Agregar al carrito" del catálogo</Button>
      <App />
    </main>
  </StrictMode>,
)
