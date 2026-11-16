// Lógica de la UI, separada de los componentes para poder probarla.

// Escenarios de la matriz de casos que se reproducen con un clic.
export const SCENARIOS = [
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

export function orderBody(customerId, items) {
  return { customerId, items }
}

const STATUS = {
  PENDING: { label: 'Pendiente', tone: 'neutral' },
  STOCK_RESERVED: { label: 'Stock reservado', tone: 'progress' },
  PAID: { label: 'Pagado', tone: 'progress' },
  APPROVED: { label: 'Aprobado', tone: 'ok' },
  REJECTED: { label: 'Rechazado', tone: 'bad' },
  CANCELLED: { label: 'Cancelado', tone: 'warn' },
}

export function statusInfo(status) {
  return STATUS[status] ?? { label: status, tone: 'neutral' }
}

export function isFinal(status) {
  return ['APPROVED', 'REJECTED', 'CANCELLED'].includes(status)
}

const COMPENSATIONS = ['StockReleased', 'PaymentRefunded']
const FAILURES = ['StockReservationFailed', 'PaymentFailed', 'ShipmentFailed', 'OrderCancelled']

export function entryKind(entry) {
  if (!entry.applied) return 'ignored'
  if (COMPENSATIONS.includes(entry.eventType)) return 'compensation'
  if (FAILURES.includes(entry.eventType)) return 'failure'
  return 'step'
}

export async function api(path, options = {}) {
  const response = await fetch(path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
    body: options.body ? JSON.stringify(options.body) : undefined,
  })
  const body = await response.json().catch(() => null)
  if (!response.ok) throw new Error(body?.detail ?? `HTTP ${response.status}`)
  return body
}
