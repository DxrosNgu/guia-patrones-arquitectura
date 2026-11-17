import { useCallback, useEffect, useState } from 'react'
import {
  listHistory, mergeWithOptimistic, placeOrder, readYourWrites, waitByPolling,
  type HistoryOrder, type Provisional,
} from './history'

type Technique = 'polling' | 'optimistic' | 'ryw'

const TECHNIQUES: { id: Technique; label: string; hint: string }[] = [
  { id: 'polling', label: 'Polling', hint: 'pregunta cada 0,5 s hasta que aparece' },
  { id: 'optimistic', label: 'UI optimista', hint: 'muestra una fila provisoria al instante' },
  { id: 'ryw', label: 'Leer lo que escribí', hint: '1 petición con la versión escrita; el servidor espera' },
]
const PRODUCTS = [
  { sku: 'INV-001', name: 'Paraguas solar', price: 49.9 },
  { sku: 'INV-002', name: 'Lámpara de agua salada', price: 89 },
  { sku: 'INV-003', name: 'Mochila con ventilador', price: 120 },
  { sku: 'INV-004', name: 'Taza que avisa cuando se enfría', price: 25.5 },
]
const STATUS_LABEL: Record<string, string> = {
  PROCESANDO: '⏳ procesando…', 'EN PROCESO': 'en proceso', RECIBIDO: 'recibido', PAGADO: 'pagado', ENVIADO: 'enviado',
}
const money = (n: number) => new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN' }).format(n)

interface Status { documents: number; eventsApplied: number; lastAppliedAt: string | null }

export default function App() {
  const [customer, setCustomer] = useState('ana')
  const [sku, setSku] = useState('INV-001')
  const [quantity, setQuantity] = useState(1)
  const [technique, setTechnique] = useState<Technique>('ryw')
  const [statusFilter, setStatusFilter] = useState('')
  const [q, setQ] = useState('')
  const [orders, setOrders] = useState<HistoryOrder[]>([])
  const [provisional, setProvisional] = useState<Provisional[]>([])
  const [message, setMessage] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [status, setStatus] = useState<Status | null>(null)

  const refresh = useCallback(async () => {
    try {
      setOrders(await listHistory(customer, statusFilter, q))
      setStatus(await (await fetch('/api/history/status')).json())
    } catch {
      // la API de lectura puede estar caída en un experimento: se mantiene lo último que se vio
    }
  }, [customer, statusFilter, q])

  useEffect(() => {
    refresh()
    const timer = setInterval(refresh, 2000)
    return () => clearInterval(timer)
  }, [refresh])

  async function buy() {
    setBusy(true)
    setMessage(null)
    const product = PRODUCTS.find((p) => p.sku === sku)!
    try {
      const placed = await placeOrder(customer, sku, quantity)
      if (technique === 'optimistic') {
        setProvisional((list) => [...list, { id: placed.id, summary: `${quantity} × ${product.name}`,
          total: placed.total, createdAt: new Date().toISOString() }])
        setMessage(`Pedido ${placed.id.slice(0, 8)}: fila provisoria al instante (0 ms). La real llega cuando la proyección la procese.`)
      } else {
        const waited = technique === 'polling' ? await waitByPolling(placed.id) : await readYourWrites(placed.id, placed.version)
        setMessage(waited.order
          ? `Pedido ${placed.id.slice(0, 8)} visible en el historial a los ${waited.ms} ms, con ${waited.requests} petición(es) al lado de lectura.`
          : `El pedido ${placed.id.slice(0, 8)} todavía no está en la vista (¿el proyector está detenido?).`)
      }
      await refresh()
    } catch (e) {
      setMessage(`No se pudo comprar: ${(e as Error).message}`)
    } finally {
      setBusy(false)
    }
  }

  const rows = mergeWithOptimistic(orders, statusFilter || q ? [] : provisional)

  return (
    <>
      <header>
        <h1>Mercado de Inventos</h1>
        <span className="muted-light">Historial de pedidos · CQRS</span>
      </header>
      <main>
        <section className="panel">
          <h2>Comprar <span className="muted">(lado de comandos: orders + Postgres)</span></h2>
          <div className="controls">
            <label>Cliente{' '}
              <select value={customer} onChange={(e) => { setCustomer(e.target.value); setProvisional([]) }}>
                <option value="ana">ana</option>
                <option value="beto">beto</option>
              </select>
            </label>
            <label>Invento{' '}
              <select value={sku} onChange={(e) => setSku(e.target.value)}>
                {PRODUCTS.map((p) => <option key={p.sku} value={p.sku}>{p.name}</option>)}
              </select>
            </label>
            <label>Cantidad{' '}
              <input type="number" min={1} max={10} value={quantity} onChange={(e) => setQuantity(Number(e.target.value))} />
            </label>
          </div>
          <div className="controls" role="radiogroup" aria-label="Técnica para la consistencia eventual">
            {TECHNIQUES.map((t) => (
              <button key={t.id} className={t.id === technique ? 'active' : ''} title={t.hint} onClick={() => setTechnique(t.id)}>
                {t.label}
              </button>
            ))}
            <button className="primary" onClick={buy} disabled={busy}>{busy ? 'Esperando la vista…' : 'Comprar'}</button>
          </div>
          <p className="muted small">{TECHNIQUES.find((t) => t.id === technique)?.hint}</p>
          {message && <p className="message" role="status">{message}</p>}
        </section>

        <section className="panel">
          <h2>Historial <span className="muted">(lado de lectura: order-history + MongoDB, se refresca cada 2 s)</span></h2>
          <div className="controls">
            <label>Estado{' '}
              <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
                <option value="">todos</option>
                <option value="RECIBIDO">recibido</option>
                <option value="PAGADO">pagado</option>
                <option value="ENVIADO">enviado</option>
              </select>
            </label>
            <label>Buscar{' '}
              <input value={q} placeholder="paraguas, taza…" onChange={(e) => setQ(e.target.value)} />
            </label>
          </div>
          <div className="table-wrap">
            <table>
              <thead>
                <tr><th>Pedido</th><th>Fecha</th><th>Qué compraste</th><th className="num">Total</th><th>Estado</th><th>Envío</th></tr>
              </thead>
              <tbody>
                {rows.map((o) => (
                  <tr key={o.id} className={o.status === 'PROCESANDO' ? 'provisional' : ''}>
                    <td>{o.id.slice(0, 8)}</td>
                    <td>{o.createdAt?.slice(0, 19).replace('T', ' ')}</td>
                    <td>{o.summary}</td>
                    <td className="num">{money(o.total ?? 0)}</td>
                    <td>{STATUS_LABEL[o.status] ?? o.status}</td>
                    <td>{o.shipment ? `${o.shipment.carrier} · ${o.shipment.trackingCode}` : '—'}</td>
                  </tr>
                ))}
                {rows.length === 0 && <tr><td colSpan={6} className="muted">Sin pedidos en la vista.</td></tr>}
              </tbody>
            </table>
          </div>
          {status && (
            <p className="muted small">
              Vista: {status.documents} pedidos · {status.eventsApplied} eventos aplicados · último:{' '}
              {status.lastAppliedAt ? status.lastAppliedAt.slice(11, 19) : '—'}
            </p>
          )}
        </section>
      </main>
    </>
  )
}
