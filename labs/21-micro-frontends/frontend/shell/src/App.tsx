import { Notice, useLocation } from '@mercado/platform'
import { on } from '@mercado/platform/events'
import { lazy, useEffect, type MouseEvent } from 'react'
import { RemoteBoundary } from './RemoteBoundary'
import { navigate, sectionFor } from './router'

// Cada import() baja el remoteEntry del remoto la PRIMERA vez que se usa, no antes.
const CatalogApp = lazy(() => import('catalog/App'))
const OrdersApp = lazy(() => import('orders/App'))
const CartBadge = lazy(() => import('orders/CartBadge'))

function link(event: MouseEvent<HTMLAnchorElement>) {
  event.preventDefault()
  navigate(event.currentTarget.getAttribute('href')!)
}

export default function App() {
  const { pathname } = useLocation()
  const section = sectionFor(pathname)

  // Los remotos piden navegar con un evento; el shell es quien cambia la URL.
  useEffect(() => on('mercado:navigate', ({ to, replace }) => navigate(to, replace)), [])

  return (
    <>
      <header className="shell-header">
        <a className="brand" href="/catalogo" onClick={link}>Mercado de Inventos</a>
        <nav aria-label="Secciones">
          <a href="/catalogo" onClick={link} aria-current={section === 'catalog' ? 'page' : undefined}>Catálogo</a>
          <a href="/pedidos" onClick={link} aria-current={section === 'orders' ? 'page' : undefined}>
            Mis pedidos{' '}
            <RemoteBoundary name="El carrito" compact><CartBadge /></RemoteBoundary>
          </a>
        </nav>
      </header>
      <main className="shell-main">
        {section === 'catalog' && <RemoteBoundary name="El catálogo"><CatalogApp /></RemoteBoundary>}
        {section === 'orders' && <RemoteBoundary name="Mis pedidos"><OrdersApp /></RemoteBoundary>}
        {section === 'not-found' && <Notice>No existe la página {pathname}.</Notice>}
      </main>
      <footer className="shell-footer mi-muted">
        Shell (equipo Plataforma) · Catálogo (equipo Catálogo) · Mis pedidos y carrito (equipo Pedidos)
      </footer>
    </>
  )
}
