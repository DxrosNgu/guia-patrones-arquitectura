// Tipos y lógica de la UI, separados de los componentes para poder probarlos.

export type SagaState =
  | 'RESERVING_STOCK' | 'CHARGING' | 'SCHEDULING_SHIPMENT' | 'COMPENSATING'
  | 'APPROVED' | 'REJECTED' | 'CANCELLED' | 'EXPIRED'

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
  status: string
  sagaState: SagaState
  attempts: number
  pendingCompensations: string | null
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
    hint: 'INV-002 tiene 0 unidades: el orquestador termina en REJECTED sin compensar.',
    orders: [{ items: [{ sku: 'INV-002', quantity: 1, unitPrice: 35 }] }],
  },
  {
    id: 'S3',
    title: 'Pago rechazado',
    hint: '725 > 500: el orquestador ORDENA a inventario liberar el stock.',
    orders: [{ items: [{ sku: 'INV-004', quantity: 10, unitPrice: 72.5 }] }],
  },
  {
    id: 'S12',
    title: 'Dos sagas, una unidad',
    hint: 'INV-005 tiene 1 unidad y llegan 2 pedidos a la vez.',
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

const STATES: Record<SagaState, { label: string; tone: Tone }> = {
  RESERVING_STOCK: { label: 'Reservando stock', tone: 'progress' },
  CHARGING: { label: 'Cobrando', tone: 'progress' },
  SCHEDULING_SHIPMENT: { label: 'Programando envío', tone: 'progress' },
  COMPENSATING: { label: 'Compensando', tone: 'warn' },
  APPROVED: { label: 'Aprobado', tone: 'ok' },
  REJECTED: { label: 'Rechazado', tone: 'bad' },
  CANCELLED: { label: 'Cancelado', tone: 'warn' },
  EXPIRED: { label: 'Expirado', tone: 'bad' },
}

export function sagaStateInfo(state: SagaState) {
  return STATES[state]
}

export function isFinal(state: SagaState): boolean {
  return ['APPROVED', 'REJECTED', 'CANCELLED', 'EXPIRED'].includes(state)
}

export type EntryKind = 'step' | 'compensation' | 'failure' | 'ignored' | 'timeout'

const COMPENSATIONS = ['StockReleased', 'PaymentRefunded']
const FAILURES = ['StockReservationFailed', 'PaymentFailed', 'ShipmentFailed', 'OrderCancelled',
  'SagaRejected', 'SagaCancelled', 'SagaExpired']

export function entryKind(entry: TimelineEntry): EntryKind {
  if (!entry.applied) return 'ignored'
  if (entry.eventType === 'Timeout') return 'timeout'
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
