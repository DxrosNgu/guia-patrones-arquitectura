import { describe, expect, it } from 'vitest'
import { bannerFor, composeInBrowser, loadViaBff } from './myOrders'

const ORDERS = [
  { id: 'ord-1', createdAt: '2026-11-02T10:15:00Z', total: 99.8,
    items: [{ sku: 'INV-001', name: 'Paraguas solar', quantity: 2, unitPrice: 49.9 }] },
]
const PAYMENTS = [{ orderId: 'ord-1', status: 'PAID' }]
const SHIPMENTS = [{ orderId: 'ord-1', status: 'IN_TRANSIT', trackingCode: 'RS-1', eta: '2026-11-19' }]

function fakeFetch(fail: string[] = []) {
  const calls: string[] = []
  const fetchImpl = async (url: string) => {
    calls.push(url)
    if (fail.some((f) => url.startsWith(f))) return new Response('caído', { status: 502 })
    const body = url.startsWith('/api/orders') ? ORDERS : url.startsWith('/api/payments') ? PAYMENTS : SHIPMENTS
    return new Response(JSON.stringify(body))
  }
  return { fetchImpl, calls }
}

describe('composeInBrowser (sin BFF)', () => {
  it('needs three requests from the browser and repeats the BFF logic', async () => {
    const { fetchImpl, calls } = fakeFetch()

    const screen = await composeInBrowser('ana', fetchImpl)

    expect(calls).toHaveLength(3)
    expect(screen.requests).toBe(3)
    expect(screen.orders[0]).toEqual({
      id: 'ord-1', date: '2026-11-02', total: 99.8, summary: '2 × Paraguas solar',
      payment: 'Pagado', shipment: 'En camino', tracking: 'RS-1', eta: '2026-11-19',
    })
  })

  it('also has to handle partial failures itself', async () => {
    const { fetchImpl } = fakeFetch(['/api/shipments'])

    const screen = await composeInBrowser('ana', fetchImpl)

    expect(screen.unavailable).toEqual(['shipping'])
    expect(screen.orders[0].shipment).toBe('Estado de envío no disponible')
  })
})

describe('loadViaBff', () => {
  it('makes a single request with the chosen mode', async () => {
    const calls: string[] = []
    const fetchImpl = async (url: string) => {
      calls.push(url)
      return new Response(JSON.stringify({ customer: 'ana', mode: 'sequential', orders: [], unavailable: [], tookMs: 5 }))
    }

    const screen = await loadViaBff('ana', 'sequential', fetchImpl)

    expect(calls).toEqual(['/bff/my-orders?customer=ana&mode=sequential'])
    expect(screen.requests).toBe(1)
  })
})

describe('bannerFor', () => {
  it('names the missing data in plain words', () => {
    expect(bannerFor([])).toBeNull()
    expect(bannerFor(['shipping', 'payments'])).toBe('Algunos datos no están disponibles ahora: envíos, pagos.')
  })
})
