// El lado de LECTURA es eventualmente consistente: después de comprar, el pedido tarda un poco
// en aparecer en el historial (lo que tarde la proyección). Tres formas de manejarlo en la web.

export interface HistoryOrder {
  id: string
  createdAt: string
  summary: string
  total: number
  status: string
  orderVersion?: number
  payment?: { status: string; at: string }
  shipment?: { status: string; carrier: string; trackingCode: string; at: string }
}

export interface Provisional {
  id: string
  summary: string
  total: number
  createdAt: string
}

export interface Waited {
  order: HistoryOrder | null
  requests: number
  ms: number
}

type FetchLike = (url: string, init?: RequestInit) => Promise<Response>
type Sleep = (ms: number) => Promise<void>
const sleep: Sleep = (ms) => new Promise((r) => setTimeout(r, ms))

export async function placeOrder(customerId: string, sku: string, quantity: number, fetchImpl: FetchLike = fetch) {
  const response = await fetchImpl('/api/orders', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ customerId, items: [{ sku, quantity }] }),
  })
  if (!response.ok) throw new Error(`pedidos respondió ${response.status}`)
  return (await response.json()) as { id: string; version: number; total: number }
}

export async function listHistory(customerId: string, status: string, q: string, fetchImpl: FetchLike = fetch) {
  const params = new URLSearchParams({ customerId })
  if (status) params.set('status', status)
  if (q) params.set('q', q)
  const response = await fetchImpl(`/api/history?${params}`)
  if (!response.ok) throw new Error(`el historial respondió ${response.status}`)
  return (await response.json()) as HistoryOrder[]
}

/** 1) Polling: preguntar cada medio segundo hasta que aparezca. Simple, pero muchas peticiones. */
export async function waitByPolling(id: string, fetchImpl: FetchLike = fetch, wait: Sleep = sleep): Promise<Waited> {
  const start = performance.now()
  for (let requests = 1; requests <= 40; requests++) {
    const response = await fetchImpl(`/api/history/${id}`)
    if (response.ok) return { order: await response.json(), requests, ms: Math.round(performance.now() - start) }
    await wait(500)
  }
  return { order: null, requests: 40, ms: Math.round(performance.now() - start) }
}

/** 3) Read-your-writes: UNA petición con la versión que escribí; el servidor espera por mí. */
export async function readYourWrites(id: string, version: number, fetchImpl: FetchLike = fetch): Promise<Waited> {
  const start = performance.now()
  const response = await fetchImpl(`/api/history/${id}?minVersion=${version}&waitMs=8000`)
  return { order: response.ok ? await response.json() : null, requests: 1, ms: Math.round(performance.now() - start) }
}

/** 2) UI optimista: mostrar YA una fila provisoria; la real la reemplaza cuando llega. */
export function mergeWithOptimistic(orders: HistoryOrder[], provisional: Provisional[]): HistoryOrder[] {
  const known = new Set(orders.map((o) => o.id))
  const pending = provisional
    .filter((p) => !known.has(p.id))
    .map((p): HistoryOrder => ({ ...p, status: 'PROCESANDO' }))
  return [...pending, ...orders]
}
