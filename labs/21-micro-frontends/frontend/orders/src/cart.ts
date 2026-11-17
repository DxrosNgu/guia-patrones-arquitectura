/**
 * El CARRITO es del equipo de pedidos: su estado, su almacenamiento y sus reglas. Se llena escuchando el
 * evento `mercado:add-to-cart`, venga de quien venga (hoy, del catálogo).
 */
import { emit, on } from '@mercado/platform/events'

export interface Line {
  sku: string
  name: string
  price: number
  quantity: number
}

export interface Order {
  id: string
  lines: Line[]
  total: number
  createdAt: string
}

type Store = Pick<Storage, 'getItem' | 'setItem'>

// Claves con prefijo del equipo: el almacenamiento del navegador es compartido entre todos los remotos.
const CART_KEY = 'orders:cart'
const HISTORY_KEY = 'orders:history'

export class Cart {
  private lines: Line[]
  private history: Order[]
  private readonly listeners = new Set<() => void>()

  constructor(private readonly cartStore: Store, private readonly historyStore: Store) {
    this.lines = JSON.parse(cartStore.getItem(CART_KEY) ?? '[]')
    this.history = JSON.parse(historyStore.getItem(HISTORY_KEY) ?? '[]')
  }

  /** Empieza a escuchar el contrato de eventos. Devuelve la función para dejar de escuchar. */
  listen(target?: EventTarget): () => void {
    return on('mercado:add-to-cart', (item) => this.add(item.sku, item.name, item.price), target)
  }

  add(sku: string, name: string, price: number, quantity = 1): void {
    const existing = this.lines.find((l) => l.sku === sku)
    this.lines = existing
      ? this.lines.map((l) => (l.sku === sku ? { ...l, quantity: l.quantity + quantity } : l))
      : [...this.lines, { sku, name, price, quantity }]
    this.changed()
  }

  remove(sku: string): void {
    this.lines = this.lines.filter((l) => l.sku !== sku)
    this.changed()
  }

  /** Confirma el carrito como un pedido y lo vacía. */
  checkout(now = new Date()): Order {
    if (this.lines.length === 0) throw new Error('El carrito está vacío')
    const order: Order = { id: `P-${now.getTime().toString(36).toUpperCase()}`, lines: this.lines,
      total: totalOf(this.lines), createdAt: now.toISOString() }
    this.history = [order, ...this.history]
    this.lines = []
    this.changed()
    return order
  }

  /** v2: vuelve a poner en el carrito lo de un pedido anterior. */
  repeat(orderId: string): void {
    this.history.find((o) => o.id === orderId)?.lines.forEach((l) => this.add(l.sku, l.name, l.price, l.quantity))
  }

  // Para useSyncExternalStore: cada cambio crea arreglos nuevos, así React ve la diferencia.
  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener)
    return () => this.listeners.delete(listener)
  }

  getLines = (): Line[] => this.lines
  getHistory = (): Order[] => this.history

  private changed(): void {
    this.cartStore.setItem(CART_KEY, JSON.stringify(this.lines))
    this.historyStore.setItem(HISTORY_KEY, JSON.stringify(this.history))
    this.listeners.forEach((l) => l())
  }
}

export const totalOf = (lines: Line[]) => Math.round(lines.reduce((sum, l) => sum + l.price * l.quantity, 0) * 100) / 100
export const countOf = (lines: Line[]) => lines.reduce((sum, l) => sum + l.quantity, 0)

/** Ir a un pedido: se le pide al shell (la URL es suya). */
export const showOrder = (id: string) => emit('mercado:navigate', { to: `/pedidos/${id}` })
