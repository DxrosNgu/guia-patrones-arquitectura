import { describe, expect, it } from 'vitest'
import { withStock } from './catalog'

describe('withStock', () => {
  const inventions = [
    { sku: 'INV-001', name: 'Paraguas solar' },
    { sku: 'INV-002', name: 'Taza' },
  ]

  it('adds the available stock to each invention', () => {
    const stock = [{ sku: 'INV-001', available: 12 }, { sku: 'INV-002', available: 0 }]

    expect(withStock(inventions, stock).map((i) => i.available)).toEqual([12, 0])
  })

  it('marks stock as unknown when inventory did not answer', () => {
    expect(withStock(inventions, null).map((i) => i.available)).toEqual([null, null])
  })
})
