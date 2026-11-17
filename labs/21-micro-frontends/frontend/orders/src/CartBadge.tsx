import { Badge } from '@mercado/platform'
import { countOf } from './cart'
import { useCartLines } from './store'

/** El contador del carrito. Vive en la barra del SHELL, pero es código (y estado) del equipo de pedidos. */
export default function CartBadge() {
  const count = countOf(useCartLines())
  return <Badge label={`${count} productos en el carrito`}>{count}</Badge>
}
