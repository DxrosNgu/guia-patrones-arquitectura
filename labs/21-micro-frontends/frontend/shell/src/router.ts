/** El router del shell: decide qué micro-frontend va en el centro según la URL. */
export type Section = 'catalog' | 'orders' | 'not-found'

export function sectionFor(pathname: string): Section {
  if (pathname === '/' || pathname === '/catalogo' || pathname.startsWith('/catalogo/')) return 'catalog'
  if (pathname === '/pedidos' || pathname.startsWith('/pedidos/')) return 'orders'
  return 'not-found'
}

/** El ÚNICO lugar que toca el historial. Después avisa con popstate, que es lo que escuchan todos (useLocation). */
export function navigate(to: string, replace = false): void {
  const current = window.location.pathname + window.location.search
  if (to === current) return
  if (replace) {
    window.history.replaceState(null, '', to)
  } else {
    window.history.pushState(null, '', to)
  }
  window.dispatchEvent(new PopStateEvent('popstate'))
}
