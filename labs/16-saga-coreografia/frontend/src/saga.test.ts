import { describe, expect, it } from 'vitest'
import { SCENARIOS, entryKind, isFinal, orderBody, statusInfo } from './saga'

describe('scenarios', () => {
  it('cover the cases of the saga matrix that a single click can reproduce', () => {
    expect(SCENARIOS.map((s) => s.id)).toEqual(['S1', 'S2', 'S3', 'S12'])
  })

  it('S3 exceeds the payment limit of 500', () => {
    const s3 = SCENARIOS.find((s) => s.id === 'S3')!
    const total = s3.orders[0].items.reduce((sum, i) => sum + i.quantity * i.unitPrice, 0)
    expect(total).toBeGreaterThan(500)
  })

  it('S12 places two orders for the last unit', () => {
    const s12 = SCENARIOS.find((s) => s.id === 'S12')!
    expect(s12.orders).toHaveLength(2)
    expect(s12.orders.every((o) => o.items[0].sku === 'INV-005')).toBe(true)
  })
})

describe('orderBody', () => {
  it('builds the request the orders API expects', () => {
    expect(orderBody('ana', [{ sku: 'INV-001', quantity: 1, unitPrice: 49.9 }])).toEqual({
      customerId: 'ana',
      items: [{ sku: 'INV-001', quantity: 1, unitPrice: 49.9 }],
    })
  })
})

describe('timeline entries', () => {
  it('classify compensations, failures, ignored events and normal steps', () => {
    expect(entryKind({ eventType: 'StockReleased', applied: true })).toBe('compensation')
    expect(entryKind({ eventType: 'PaymentRefunded', applied: true })).toBe('compensation')
    expect(entryKind({ eventType: 'PaymentFailed', applied: true })).toBe('failure')
    expect(entryKind({ eventType: 'ShipmentScheduled', applied: false })).toBe('ignored')
    expect(entryKind({ eventType: 'StockReserved', applied: true })).toBe('step')
  })
})

describe('status', () => {
  it('knows which states end the saga', () => {
    expect(isFinal('APPROVED')).toBe(true)
    expect(isFinal('CANCELLED')).toBe(true)
    expect(isFinal('STOCK_RESERVED')).toBe(false)
  })

  it('has a label for every state', () => {
    for (const s of ['PENDING', 'STOCK_RESERVED', 'PAID', 'APPROVED', 'REJECTED', 'CANCELLED'] as const) {
      expect(statusInfo(s).label).toBeTruthy()
    }
  })
})
