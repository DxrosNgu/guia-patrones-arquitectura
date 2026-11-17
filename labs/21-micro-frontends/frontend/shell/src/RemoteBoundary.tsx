import { Button, Notice } from '@mercado/platform'
import { Component, Suspense, type ReactNode } from 'react'

/**
 * El AISLAMIENTO DE FALLAS del shell. Si un remoto no carga (su contenedor está caído, su remoteEntry
 * da 502) o explota al renderizar, solo su recuadro muestra un aviso: el resto de la página sigue.
 */
class ErrorBoundary extends Component<{ fallback: ReactNode; children: ReactNode }, { failed: boolean }> {
  state = { failed: false }

  static getDerivedStateFromError() {
    return { failed: true }
  }

  componentDidCatch(error: unknown) {
    console.error('Un micro-frontend falló:', error)
  }

  render() {
    return this.state.failed ? this.props.fallback : this.props.children
  }
}

export function RemoteBoundary({ name, compact = false, children }: { name: string; compact?: boolean; children: ReactNode }) {
  const fallback = compact
    ? <span className="mi-badge" title={`${name}: no disponible`} aria-label={`${name} no disponible`}>–</span>
    : (
      <Notice>
        <strong>{name} no está disponible ahora.</strong> El resto de la tienda sigue funcionando.{' '}
        <Button onClick={() => window.location.reload()}>Reintentar</Button>
      </Notice>
    )
  return (
    <ErrorBoundary fallback={fallback}>
      <Suspense fallback={compact ? null : <p className="mi-muted">Cargando {name}…</p>}>{children}</Suspense>
    </ErrorBoundary>
  )
}
