import { describe, expect, it } from 'vitest'
import { SCENARIOS, entryKind, isFinal, orderBody, sagaStateInfo, type TimelineEntry } from './saga'

const entry = (eventType: string, applied = true): TimelineEntry =>
  ({ id: 1, at: '2026-11-16T10:00:00Z', eventType, description: '', applied })

describe('scenarios', () => {
  it('cover the cases a single click can reproduce', () => {
    expect(SCENARIOS.map((s) => s.id)).toEqual(['S1', 'S2', 'S3', 'S12'])
  })

  it('S3 exceeds the payment limit of 500', () => {
    const s3 = SCENARIOS.find((s) => s.id === 'S3')!
    const total = s3.orders[0].items.reduce((sum, i) => sum + i.quantity * i.unitPrice, 0)
    expect(total).toBeGreaterThan(500)
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
  it('classify timeouts, compensations, failures, ignored replies and normal steps', () => {
    expect(entryKind(entry('Timeout'))).toBe('timeout')
    expect(entryKind(entry('StockReleased'))).toBe('compensation')
    expect(entryKind(entry('PaymentRefunded'))).toBe('compensation')
    expect(entryKind(entry('PaymentFailed'))).toBe('failure')
    expect(entryKind(entry('SagaExpired'))).toBe('failure')
    expect(entryKind(entry('PaymentCompleted', false))).toBe('ignored')
    expect(entryKind(entry('StockReserved'))).toBe('step')
  })
})

describe('saga states', () => {
  it('know which ones end the saga', () => {
    expect(isFinal('APPROVED')).toBe(true)
    expect(isFinal('EXPIRED')).toBe(true)
    expect(isFinal('COMPENSATING')).toBe(false)
  })

  it('have a label for every state of the orchestrator', () => {
    const states = ['RESERVING_STOCK', 'CHARGING', 'SCHEDULING_SHIPMENT', 'COMPENSATING',
      'APPROVED', 'REJECTED', 'CANCELLED', 'EXPIRED'] as const
    for (const s of states) expect(sagaStateInfo(s).label).toBeTruthy()
  })
})
