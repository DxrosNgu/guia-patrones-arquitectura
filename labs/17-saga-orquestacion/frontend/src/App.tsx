import { useCallback, useEffect, useState } from 'react'
import {
  SCENARIOS, api, entryKind, isFinal, orderBody, sagaStateInfo,
  type EntryKind, type OrderView, type SagaState, type Scenario,
} from './saga'

type CarrierMode = 'ok' | 'down' | 'reject'

const CARRIER_MODES: { mode: CarrierMode; label: string; hint: string }[] = [
  { mode: 'ok', label: 'Funciona', hint: 'Acepta los envíos' },
  { mode: 'down', label: 'Caído', hint: 'S4: después del pivote → se reintenta' },
  { mode: 'reject', label: 'Rechaza', hint: 'S5: falla definitiva → se compensa' },
]

const PAYMENT_DELAYS = [
  { delayMs: 0, label: 'Normal', hint: 'Pagos responde al instante' },
  { delayMs: 8000, label: 'Lento (8 s)', hint: 'S6: el orquestador espera 5 s y reenvía ChargePayment' },
]

const ICON: Record<EntryKind, string> = { step: '●', compensation: '↩', failure: '✕', ignored: '○', timeout: '⏱' }

function usePolling(fn: () => Promise<void>, ms = 1000) {
  useEffect(() => {
    let alive = true
    const tick = () => { fn().catch(() => {}).finally(() => { if (alive) setTimeout(tick, ms) }) }
    tick()
    return () => { alive = false }
  }, [fn, ms])
}

function StateChip({ state }: { state: SagaState }) {
  const info = sagaStateInfo(state)
  return <span className={`chip tone-${info.tone}`}>{info.label}</span>
}

function Timeline({ order, onCancel }: { order: OrderView | null; onCancel: (id: string) => void }) {
  if (!order) return <p className="muted">Elige un pedido para ver su saga paso a paso.</p>
  return (
    <div>
      <div className="row">
        <code className="id">{order.id.slice(0, 8)}</code>
        <StateChip state={order.sagaState} />
        {order.pendingCompensations && <span className="muted">pendiente: {order.pendingCompensations}</span>}
        {['RESERVING_STOCK', 'CHARGING'].includes(order.sagaState) && (
          <button className="danger small" onClick={() => onCancel(order.id)}>Cancelar (S11)</button>
        )}
      </div>
      <ol className="timeline">
        {(order.timeline ?? []).map((entry) => {
          const kind = entryKind(entry)
          return (
            <li key={entry.id} className={`entry ${kind}`}>
              <span className="icon" aria-hidden>{ICON[kind]}</span>
              <div>
                <div className="entry-type">{entry.eventType}</div>
                <div className="entry-text">{entry.description}</div>
              </div>
              <time>{new Date(entry.at).toLocaleTimeString()}</time>
            </li>
          )
        })}
      </ol>
      {!isFinal(order.sagaState) && <p className="muted waiting">⏳ El orquestador espera la respuesta del paso actual…</p>}
    </div>
  )
}

export default function App() {
  const [orders, setOrders] = useState<OrderView[]>([])
  const [stock, setStock] = useState<{ sku: string; available: number }[]>([])
  const [carrier, setCarrier] = useState<CarrierMode>('ok')
  const [delayMs, setDelayMs] = useState(0)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [selected, setSelected] = useState<OrderView | null>(null)
  const [error, setError] = useState<string | null>(null)

  usePolling(useCallback(async () => {
    const [list, inventory, carrierState, chaos] = await Promise.all([
      api<OrderView[]>('/api/orders'),
      api<{ sku: string; available: number }[]>('/api/inventory/stock'),
      api<{ mode: CarrierMode }>('/api/shipping/carrier'),
      api<{ delayMs: number }>('/api/payments/chaos'),
    ])
    setOrders(list)
    setStock(inventory)
    setCarrier(carrierState.mode)
    setDelayMs(chaos.delayMs)
  }, []))

  usePolling(useCallback(async () => {
    if (selectedId) setSelected(await api<OrderView>(`/api/orders/${selectedId}`))
  }, [selectedId]), 700)

  async function runScenario(scenario: Scenario) {
    setError(null)
    try {
      const created = await Promise.all(scenario.orders.map((o) =>
        api<OrderView>('/api/orders', { method: 'POST', body: orderBody('ana', o.items) })))
      setSelectedId(created[0].id)
    } catch (e) {
      setError((e as Error).message)
    }
  }

  async function cancel(id: string) {
    setError(null)
    try {
      setSelected(await api<OrderView>(`/api/orders/${id}/cancel`, { method: 'POST' }))
    } catch (e) {
      setError((e as Error).message)
    }
  }

  return (
    <>
      <header>
        <h1>Saga por orquestación</h1>
        <span className="muted-light">el orquestador (en orders) envía comandos y espera respuestas</span>
      </header>
      <main>
        {error && <p className="alert">{error}</p>}
        <section className="grid">
          <div className="panel">
            <h2>1 · Lanza un escenario</h2>
            {SCENARIOS.map((s) => (
              <button key={s.id} className="scenario" onClick={() => runScenario(s)}>
                <b>{s.id}</b> {s.title}
                <small>{s.hint}</small>
              </button>
            ))}
            <h2>Pagos (caos)</h2>
            <div className="segmented">
              {PAYMENT_DELAYS.map((d) => (
                <button key={d.delayMs} className={delayMs === d.delayMs ? 'active' : ''} title={d.hint}
                  onClick={async () => setDelayMs((await api<{ delayMs: number }>('/api/payments/chaos',
                    { method: 'PUT', body: { delayMs: d.delayMs } })).delayMs)}>
                  {d.label}
                </button>
              ))}
            </div>
            <p className="muted small-text">{PAYMENT_DELAYS.find((d) => d.delayMs === delayMs)?.hint ?? `${delayMs} ms`}</p>
            <h2>Transportista (caos)</h2>
            <div className="segmented">
              {CARRIER_MODES.map((m) => (
                <button key={m.mode} className={carrier === m.mode ? 'active' : ''} title={m.hint}
                  onClick={async () => setCarrier((await api<{ mode: CarrierMode }>('/api/shipping/carrier',
                    { method: 'PUT', body: { mode: m.mode } })).mode)}>
                  {m.label}
                </button>
              ))}
            </div>
            <p className="muted small-text">{CARRIER_MODES.find((m) => m.mode === carrier)?.hint}</p>
            <h2>Stock</h2>
            <table>
              <tbody>
                {stock.map((s) => <tr key={s.sku}><td>{s.sku}</td><td className="num">{s.available}</td></tr>)}
              </tbody>
            </table>
          </div>

          <div className="panel">
            <h2>2 · Sagas</h2>
            {orders.length === 0 && <p className="muted">Todavía no hay pedidos.</p>}
            <ul className="orders">
              {orders.map((o) => (
                <li key={o.id} className={o.id === selectedId ? 'selected' : ''} onClick={() => setSelectedId(o.id)}>
                  <code className="id">{o.id.slice(0, 8)}</code>
                  <span>{o.items.map((i) => `${i.quantity}×${i.sku}`).join(', ')}</span>
                  <StateChip state={o.sagaState} />
                </li>
              ))}
            </ul>
          </div>

          <div className="panel">
            <h2>3 · La saga, paso a paso</h2>
            <Timeline order={selected} onCancel={cancel} />
          </div>
        </section>
      </main>
    </>
  )
}
