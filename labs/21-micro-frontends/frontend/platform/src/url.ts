/**
 * LA URL, el segundo canal entre equipos. El shell es el único que cambia el historial (pushState) y
 * después avisa con un `popstate`; cada micro-frontend LEE la URL con este hook y se re-renderiza.
 * Así un link compartido ("/catalogo?q=taza", "/pedidos/P-42") abre la misma pantalla para cualquiera.
 */
import { useSyncExternalStore } from 'react'

function subscribe(onChange: () => void): () => void {
  window.addEventListener('popstate', onChange)
  return () => window.removeEventListener('popstate', onChange)
}

const snapshot = () => window.location.pathname + window.location.search

export function useLocation(): { pathname: string; search: string } {
  const href = useSyncExternalStore(subscribe, snapshot)
  const url = new URL(href, 'http://local')
  return { pathname: url.pathname, search: url.search }
}
