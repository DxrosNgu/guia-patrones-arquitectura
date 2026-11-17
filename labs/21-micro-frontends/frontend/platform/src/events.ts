/**
 * EL CONTRATO ENTRE EQUIPOS: los eventos del navegador que se mandan los micro-frontends.
 *
 * Es la única "API" entre catálogo, pedidos y el shell en el navegador. Ninguno importa código del otro:
 * uno emite un CustomEvent en `window` y el que le interesa lo escucha. Cambiar un evento (renombrarlo,
 * quitarle un campo) rompe a otro equipo, igual que cambiar una API REST: se agrega, no se cambia.
 */
export interface MercadoEvents {
  /** Catálogo → pedidos: el cliente quiere comprar algo. */
  'mercado:add-to-cart': { sku: string; name: string; price: number }
  /**
   * Cualquiera → shell: ir a otra URL. Solo el shell toca el historial del navegador; los remotos piden.
   * `replace` cambia la URL sin agregar una entrada al historial (por ejemplo, al escribir en un buscador).
   */
  'mercado:navigate': { to: string; replace?: boolean }
}

export type MercadoEvent = keyof MercadoEvents

const defaultTarget = (): EventTarget => window

export function emit<K extends MercadoEvent>(type: K, detail: MercadoEvents[K], target: EventTarget = defaultTarget()): void {
  target.dispatchEvent(new CustomEvent(type, { detail }))
}

/** Escucha un evento. Devuelve la función para dejar de escucharlo (úsala en el cleanup de un useEffect). */
export function on<K extends MercadoEvent>(
  type: K,
  handler: (detail: MercadoEvents[K]) => void,
  target: EventTarget = defaultTarget(),
): () => void {
  const listener = (event: Event) => handler((event as CustomEvent<MercadoEvents[K]>).detail)
  target.addEventListener(type, listener)
  return () => target.removeEventListener(type, listener)
}
