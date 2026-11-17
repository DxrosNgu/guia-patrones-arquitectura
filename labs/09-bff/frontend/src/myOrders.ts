// La pantalla "Mis pedidos" se puede cargar de dos formas:
//  - Con el BFF: UNA petición; el BFF compone y devuelve los textos listos.
//  - Sin BFF: el NAVEGADOR hace 3 peticiones, compone y traduce los estados. Es la misma
//    lógica que el BFF, repetida en TypeScript... y habría que repetirla en cada app (web, móvil).

export type Mode = 'parallel' | 'sequential' | 'browser'

export interface OrderRow {
  id: string
  date: string
  total: number
  summary: string
  payment: string
  shipment: string
  tracking: string | null
  eta: string | null
}

export interface Screen {
  customer: string
  mode: Mode
  orders: OrderRow[]
  unavailable: string[]
  tookMs: number
  /** Cuántas peticiones hizo el navegador para armar la pantalla. */
  requests: number
}

type FetchLike = (url: string) => Promise<Response>

export async function loadViaBff(customer: string, mode: 'parallel' | 'sequential',
                                 fetchImpl: FetchLike = fetch): Promise<Screen> {
  const response = await fetchImpl(`/bff/my-orders?customer=${encodeURIComponent(customer)}&mode=${mode}`)
  if (!response.ok) throw new Error(`El BFF respondió ${response.status}`)
  return { ...(await response.json()), requests: 1 }
}

// ── Sin BFF: todo esto vive en el navegador ───────────────────────────────

interface Order { id: string; createdAt: string; total: number; items: { name: string; quantity: number }[] }
interface Payment { orderId: string; status: string }
interface Shipment { orderId: string; status: string; trackingCode?: string; eta?: string }

const PAYMENT_LABELS: Record<string, string> = { PAID: 'Pagado', PENDING: 'Pago pendiente', REFUNDED: 'Reembolsado' }
const SHIPMENT_LABELS: Record<string, string> = { PREPARING: 'Preparando', IN_TRANSIT: 'En camino', DELIVERED: 'Entregado' }

async function getJson<T>(fetchImpl: FetchLike, url: string): Promise<T> {
  const response = await fetchImpl(url)
  if (!response.ok) throw new Error(`${url} respondió ${response.status}`)
  return response.json() as Promise<T>
}

export async function composeInBrowser(customer: string, fetchImpl: FetchLike = fetch): Promise<Screen> {
  const start = performance.now()
  const orders = await getJson<Order[]>(fetchImpl, `/api/orders?customerId=${encodeURIComponent(customer)}`)
  const ids = orders.map((o) => o.id).join(',')
  const unavailable: string[] = []
  const optional = async <T,>(name: string, url: string): Promise<T[] | null> => {
    try {
      return await getJson<T[]>(fetchImpl, url)
    } catch {
      unavailable.push(name)
      return null
    }
  }
  const [payments, shipments] = await Promise.all([
    optional<Payment>('payments', `/api/payments?orderIds=${ids}`),
    optional<Shipment>('shipping', `/api/shipments?orderIds=${ids}`),
  ])

  const rows = orders.map((order): OrderRow => {
    const payment = payments?.find((p) => p.orderId === order.id)
    const shipment = shipments?.find((s) => s.orderId === order.id)
    return {
      id: order.id,
      date: order.createdAt.slice(0, 10),
      total: order.total,
      summary: order.items.map((i) => `${i.quantity} × ${i.name}`).join(', '),
      payment: payments === null ? 'Estado de pago no disponible'
        : payment ? PAYMENT_LABELS[payment.status] ?? payment.status : 'Sin pago',
      shipment: shipments === null ? 'Estado de envío no disponible'
        : shipment ? SHIPMENT_LABELS[shipment.status] ?? shipment.status : 'Aún no se envía',
      tracking: shipment?.trackingCode ?? null,
      eta: shipment?.eta ?? null,
    }
  })
  return { customer, mode: 'browser', orders: rows, unavailable: unavailable.sort(),
           tookMs: Math.round(performance.now() - start), requests: 3 }
}

const SERVICE_NAMES: Record<string, string> = { payments: 'pagos', shipping: 'envíos', orders: 'pedidos' }

export function bannerFor(unavailable: string[]): string | null {
  if (unavailable.length === 0) return null
  return `Algunos datos no están disponibles ahora: ${unavailable.map((s) => SERVICE_NAMES[s] ?? s).join(', ')}.`
}
