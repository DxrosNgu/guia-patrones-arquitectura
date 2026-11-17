import { describe, expect, it } from 'vitest'
import { sectionFor } from './router'

describe('router del shell', () => {
  it.each([
    ['/', 'catalog'],
    ['/catalogo', 'catalog'],
    ['/catalogo/INV-001', 'catalog'],
    ['/pedidos', 'orders'],
    ['/pedidos/P-42', 'orders'],
    ['/catalogos', 'not-found'],
    ['/admin', 'not-found'],
  ])('%s → %s', (path, section) => {
    expect(sectionFor(path)).toBe(section)
  })
})
