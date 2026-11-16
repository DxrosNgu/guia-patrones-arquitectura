// Tipos y lógica de la UI, separados de los componentes para poder probarlos.

export type OrderStatus = 'PENDING' | 'STOCK_RESERVED' | 'PAID' | 'APPROVED' | 'REJECTED' | 'CANCELLED'

export interface Item {
  sku: string
  quantity: number
  unitPrice: number
}

export interface TimelineEntry {
  id: number
  at: string
  eventType: string
  description: string
  applied: boolean
}

export interface OrderView {
  id: string
  customerId: string
  status: OrderStatus
  total: number
  items: Item[]
  timeline: TimelineEntry[] | null
}

export interface Scenario {
  id: string
  title: string
  hint: string
  orders: { items: Item[] }[]
}

// Escenarios de la matriz de casos que se reproducen con un clic.
export const SCENARIOS: Scenario[] = [
  {
    id: 'S1',
    title: 'Camino feliz',
    hint: 'Hay stock, el pago pasa y el transportista acepta.',
    orders: [{ items: [{ sku: 'INV-001', quantity: 1, unitPrice: 49.9 }] }],
  },
  {
    id: 'S2',
    title: 'Sin stock',
    hint: 'INV-002 tiene 0 unidades: inventario rechaza y no hay nada que compensar.',
    orders: [{ items: [{ sku: 'INV-002', quantity: 1, unitPrice: 35 }] }],
  },
  {
    id: 'S3',
    title: 'Pago rechazado',
    hint: '10 × 72,50 = 725 > 500: pagos rechaza e inventario COMPENSA liberando el stock.',
    orders: [{ items: [{ sku: 'INV-004', quantity: 10, unitPrice: 72.5 }] }],
  },
  {
    id: 'S12',
    title: 'Dos sagas, una unidad',
    hint: 'INV-005 tiene 1 unidad y llegan 2 pedidos a la vez: solo uno la consigue.',
    orders: [
      { items: [{ sku: 'INV-005', quantity: 1, unitPrice: 120 }] },
      { items: [{ sku: 'INV-005', quantity: 1, unitPrice: 120 }] },
    ],
  },
]

export function orderBody(customerId: string, items: Item[]) {
  return { customerId, items }
}

type Tone = 'neutral' | 'progress' | 'ok' | 'bad' | 'warn'

const STATUS: Record<OrderStatus, { label: string; tone: Tone }> = {
  PENDING: { label: 'Pendiente', tone: 'neutral' },
  STOCK_RESERVED: { label: 'Stock reservado', tone: 'progress' },
  PAID: { label: 'Pagado', tone: 'progress' },
  APPROVED: { label: 'Aprobado', tone: 'ok' },
  REJECTED: { label: 'Rechazado', tone: 'bad' },
  CANCELLED: { label: 'Cancelado', tone: 'warn' },
}

export function statusInfo(status: OrderStatus) {
  return STATUS[status] ?? { label: status, tone: 'neutral' as Tone }
}

export function isFinal(status: OrderStatus): boolean {
  return ['APPROVED', 'REJECTED', 'CANCELLED'].includes(status)
}

const COMPENSATIONS = ['StockReleased', 'PaymentRefunded']
const FAILURES = ['StockReservationFailed', 'PaymentFailed', 'ShipmentFailed', 'OrderCancelled']

export type EntryKind = 'step' | 'compensation' | 'failure' | 'ignored'

export function entryKind(entry: Pick<TimelineEntry, 'eventType' | 'applied'>): EntryKind {
  if (!entry.applied) return 'ignored'
  if (COMPENSATIONS.includes(entry.eventType)) return 'compensation'
  if (FAILURES.includes(entry.eventType)) return 'failure'
  return 'step'
}

export async function api<T>(path: string, options: { method?: string; body?: unknown } = {}): Promise<T> {
  const response = await fetch(path, {
    method: options.method,
    headers: { 'Content-Type': 'application/json' },
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  })
  const body = await response.json().catch(() => null)
  if (!response.ok) throw new Error(body?.detail ?? `HTTP ${response.status}`)
  return body as T
}
