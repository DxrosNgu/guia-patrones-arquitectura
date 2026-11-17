import { Button, Card, Price, useLocation } from '@mercado/platform'
import { emit } from '@mercado/platform/events'
import { useState } from 'react'
import { PRODUCTS, filterProducts } from './products'
import './catalog.css'

/**
 * La página del equipo CATÁLOGO. No sabe que existe un carrito ni un equipo de pedidos:
 * cuando el cliente quiere comprar, EMITE un evento. Quien lo escuche, sabrá qué hacer.
 */
export default function CatalogApp() {
  const { search } = useLocation()
  const query = new URLSearchParams(search).get('q') ?? ''
  const [added, setAdded] = useState<string | null>(null)
  const products = filterProducts(PRODUCTS, query)

  // La búsqueda vive en la URL (?q=taza): se puede compartir el link y el botón "atrás" funciona.
  const setQuery = (value: string) =>
    emit('mercado:navigate', { to: value ? `/catalogo?q=${encodeURIComponent(value)}` : '/catalogo', replace: true })

  const add = (sku: string, name: string, price: number) => {
    emit('mercado:add-to-cart', { sku, name, price })
    setAdded(name)
  }

  return (
    <div className="catalog">
      <div className="catalog-toolbar">
        <h1>Catálogo</h1>
        <input className="mi-input" type="search" placeholder="Buscar: lámpara, café…" aria-label="Buscar inventos"
          value={query} onChange={(e) => setQuery(e.target.value)} />
      </div>
      {added && (
        <p className="catalog-added" role="status">
          ✓ {added} está en el carrito ·{' '}
          <a href="/pedidos" onClick={(e) => { e.preventDefault(); emit('mercado:navigate', { to: '/pedidos' }) }}>ver mis pedidos</a>
        </p>
      )}
      <div className="catalog-grid">
        {products.map((p) => (
          <Card key={p.sku} title={p.name}>
            <p className="mi-muted catalog-description">{p.description}</p>
            <div className="catalog-buy">
              <strong><Price value={p.price} /></strong>
              <Button variant="primary" onClick={() => add(p.sku, p.name, p.price)}>Agregar al carrito</Button>
            </div>
          </Card>
        ))}
        {products.length === 0 && <p className="mi-muted">Ningún invento coincide con “{query}”.</p>}
      </div>
    </div>
  )
}
