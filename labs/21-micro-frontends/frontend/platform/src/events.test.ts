import { describe, expect, it } from 'vitest'
import { emit, on } from './events'

describe('contrato de eventos', () => {
  it('lo que emite un equipo lo recibe otro, con el payload tipado', () => {
    const target = new EventTarget()
    const received: string[] = []
    on('mercado:add-to-cart', (item) => received.push(`${item.sku}:${item.price}`), target)

    emit('mercado:add-to-cart', { sku: 'INV-001', name: 'Paraguas solar', price: 49.9 }, target)

    expect(received).toEqual(['INV-001:49.9'])
  })

  it('después de dejar de escuchar, no llega nada', () => {
    const target = new EventTarget()
    let calls = 0
    const off = on('mercado:navigate', () => calls++, target)

    emit('mercado:navigate', { to: '/pedidos' }, target)
    off()
    emit('mercado:navigate', { to: '/catalogo' }, target)

    expect(calls).toBe(1)
  })
})
