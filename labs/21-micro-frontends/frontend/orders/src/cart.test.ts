import { emit } from '@mercado/platform/events'
import { describe, expect, it } from 'vitest'
import { Cart, countOf, totalOf } from './cart'

const memory = () => {
  const data = new Map<string, string>()
  return { getItem: (k: string) => data.get(k) ?? null, setItem: (k: string, v: string) => void data.set(k, v) }
}

describe('carrito del equipo de pedidos', () => {
  it('se llena con el evento del catálogo, sin conocer al catálogo', () => {
    const target = new EventTarget()
    const cart = new Cart(memory(), memory())
    cart.listen(target)

    emit('mercado:add-to-cart', { sku: 'INV-001', name: 'Paraguas solar', price: 49.9 }, target)
    emit('mercado:add-to-cart', { sku: 'INV-001', name: 'Paraguas solar', price: 49.9 }, target)
    emit('mercado:add-to-cart', { sku: 'INV-004', name: 'Taza', price: 25.5 }, target)

    expect(cart.getLines().map((l) => `${l.quantity}×${l.sku}`)).toEqual(['2×INV-001', '1×INV-004'])
    expect(totalOf(cart.getLines())).toBe(125.3)
    expect(countOf(cart.getLines())).toBe(3)
  })

  it('confirmar crea un pedido en el historial y vacía el carrito; sobrevive a recargar la página', () => {
    const cartStore = memory()
    const historyStore = memory()
    const cart = new Cart(cartStore, historyStore)
    cart.add('INV-002', 'Lámpara de agua salada', 89)

    const order = cart.checkout(new Date('2026-11-17T10:00:00Z'))

    expect(order.total).toBe(89)
    expect(cart.getLines()).toEqual([])
    const reloaded = new Cart(cartStore, historyStore)
    expect(reloaded.getHistory().map((o) => o.id)).toEqual([order.id])
  })

  it('repetir un pedido vuelve a poner sus productos en el carrito', () => {
    const cart = new Cart(memory(), memory())
    cart.add('INV-003', 'Mochila con ventilador', 120, 2)
    const order = cart.checkout()

    cart.repeat(order.id)

    expect(cart.getLines()).toEqual([{ sku: 'INV-003', name: 'Mochila con ventilador', price: 120, quantity: 2 }])
  })
})
