import { Button, Card, Notice, Price, formatPrice, useLocation } from '@mercado/platform'
import { emit } from '@mercado/platform/events'
import { countOf, showOrder, totalOf } from './cart'
import { VERSION, cart, useCartLines, useHistory } from './store'
import './orders.css'

/** La página del equipo PEDIDOS: el carrito, confirmar y el historial. */
export default function OrdersApp() {
  const lines = useCartLines()
  const history = useHistory()
  const { pathname } = useLocation()
  const highlighted = pathname.startsWith('/pedidos/') ? decodeURIComponent(pathname.slice('/pedidos/'.length)) : null

  return (
    <div className="orders">
      <h1>Mis pedidos</h1>
      {highlighted && history.some((o) => o.id === highlighted) && (
        <Notice>✓ Pedido <strong>{highlighted}</strong> confirmado.</Notice>
      )}

      <Card title={`Carrito (${countOf(lines)})`}>
        {lines.length === 0 ? (
          <p className="mi-muted">
            Vacío.{' '}
            <a href="/catalogo" onClick={(e) => { e.preventDefault(); emit('mercado:navigate', { to: '/catalogo' }) }}>Ir al catálogo</a>
          </p>
        ) : (
          <>
            <ul className="orders-lines">
              {lines.map((l) => (
                <li key={l.sku}>
                  <span>{l.quantity} × {l.name}</span>
                  <span><Price value={l.price * l.quantity} /> <Button onClick={() => cart.remove(l.sku)} aria-label={`Quitar ${l.name}`}>Quitar</Button></span>
                </li>
              ))}
            </ul>
            <div className="orders-total">
              <strong>Total: <Price value={totalOf(lines)} /></strong>
              <Button variant="primary" onClick={() => showOrder(cart.checkout().id)}>Confirmar pedido</Button>
            </div>
          </>
        )}
      </Card>

      <Card title="Historial">
        {history.length === 0 ? <p className="mi-muted">Todavía no hay pedidos.</p> : (
          <ul className="orders-history">
            {history.map((o) => (
              <li key={o.id} className={o.id === highlighted ? 'orders-highlight' : ''}>
                <span><strong>{o.id}</strong> · {new Date(o.createdAt).toLocaleString('es-PE')} · {countOf(o.lines)} productos · {formatPrice(o.total)}</span>
                {VERSION === '2' && <Button onClick={() => cart.repeat(o.id)}>Repetir pedido</Button>}
              </li>
            ))}
          </ul>
        )}
      </Card>
      <p className="mi-muted orders-version">Mis pedidos · versión {VERSION}</p>
    </div>
  )
}
