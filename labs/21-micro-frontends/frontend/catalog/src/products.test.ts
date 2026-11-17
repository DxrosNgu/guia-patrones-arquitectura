import { describe, expect, it } from 'vitest'
import { PRODUCTS, filterProducts } from './products'

describe('búsqueda del catálogo', () => {
  it('ignora tildes y mayúsculas', () => {
    expect(filterProducts(PRODUCTS, 'LAMPARA').map((p) => p.sku)).toEqual(['INV-002'])
  })

  it('busca también en la descripción', () => {
    expect(filterProducts(PRODUCTS, 'café').map((p) => p.sku)).toEqual(['INV-004'])
  })

  it('sin búsqueda devuelve todo', () => {
    expect(filterProducts(PRODUCTS, '  ')).toHaveLength(PRODUCTS.length)
  })
})
