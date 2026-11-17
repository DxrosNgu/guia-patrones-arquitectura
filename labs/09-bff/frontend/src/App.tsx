import { useCallback, useEffect, useState } from 'react'
import { bannerFor, composeInBrowser, loadViaBff, type Mode, type Screen } from './myOrders'

const MODES: { mode: Mode; label: string; hint: string }[] = [
  { mode: 'parallel', label: 'BFF · en paralelo', hint: 'el BFF llama a pagos y envíos a la vez' },
  { mode: 'sequential', label: 'BFF · en cascada', hint: 'el BFF llama a pagos y después a envíos' },
  { mode: 'browser', label: 'Sin BFF', hint: 'el navegador llama a cada servicio y compone él mismo' },
]

const money = (n: number) => new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN' }).format(n)

export default function App() {
  const [customer, setCustomer] = useState('ana')
  const [mode, setMode] = useState<Mode>('parallel')
  const [screen, setScreen] = useState<Screen | null>(null)
  const [browserMs, setBrowserMs] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    const start = performance.now()
    try {
      setScreen(mode === 'browser' ? await composeInBrowser(customer) : await loadViaBff(customer, mode))
      setBrowserMs(Math.round(performance.now() - start))
    } catch (e) {
      setScreen(null)
      setError((e as Error).message)
    } finally {
      setLoading(false)
    }
  }, [customer, mode])

  useEffect(() => {
    load()
  }, [load])

  const banner = screen ? bannerFor(screen.unavailable) : null

  return (
    <>
      <header>
        <h1>Mercado de Inventos</h1>
        <span className="muted-light">Mis pedidos</span>
      </header>
      <main>
        <section className="controls">
          <label>
            Cliente{' '}
            <select value={customer} onChange={(e) => setCustomer(e.target.value)}>
              <option value="ana">ana</option>
              <option value="beto">beto</option>
            </select>
          </label>
          <div className="modes" role="group" aria-label="Cómo se carga la pantalla">
            {MODES.map((m) => (
              <button key={m.mode} className={m.mode === mode ? 'active' : ''} title={m.hint}
                      onClick={() => setMode(m.mode)}>
                {m.label}
              </button>
            ))}
          </div>
          <button onClick={load} disabled={loading}>{loading ? 'Cargando…' : 'Recargar'}</button>
        </section>

        {screen && (
          <p className="metrics" data-testid="metrics">
            {MODES.find((m) => m.mode === mode)?.hint} · <b>{screen.requests}</b> petición(es) del navegador ·
            {' '}composición: <b>{screen.tookMs} ms</b> · total en el navegador: <b>{browserMs} ms</b>
          </p>
        )}
        {banner && <p className="banner" role="status">⚠️ {banner}</p>}
        {error && <p className="error" role="alert">No se pudo cargar la pantalla: {error}</p>}

        {screen && (
          <div className="table-wrap">
            <table>
              <thead>
                <tr><th>Pedido</th><th>Fecha</th><th>Qué compraste</th><th className="num">Total</th><th>Pago</th><th>Envío</th></tr>
              </thead>
              <tbody>
                {screen.orders.map((o) => (
                  <tr key={o.id}>
                    <td>{o.id}</td>
                    <td>{o.date}</td>
                    <td>{o.summary}</td>
                    <td className="num">{money(o.total)}</td>
                    <td>{o.payment}</td>
                    <td className={o.shipment.includes('no disponible') ? 'muted' : ''}>
                      {o.shipment}
                      {o.tracking && <div className="small">{o.tracking} · llega {o.eta}</div>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </main>
    </>
  )
}
