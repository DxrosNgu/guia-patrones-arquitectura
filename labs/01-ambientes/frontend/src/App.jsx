import { useEffect, useState } from 'react'
import { loadConfig, findEnvironmentMismatches } from './config.js'
import { fetchJson, withStock } from './catalog.js'

const config = loadConfig()

function formatPrice(price, currency) {
  return new Intl.NumberFormat('es-PE', { style: 'currency', currency }).format(price)
}

function ServiceCard({ title, info, rows }) {
  return (
    <article className="card">
      <h3>{title}</h3>
      {info ? (
        <dl>
          {rows.map(([label, value]) => (
            <div key={label}>
              <dt>{label}</dt>
              <dd>{String(value)}</dd>
            </div>
          ))}
        </dl>
      ) : (
        <p className="muted">No responde</p>
      )}
    </article>
  )
}

export default function App() {
  const [catalogInfo, setCatalogInfo] = useState(null)
  const [inventoryInfo, setInventoryInfo] = useState(null)
  const [inventions, setInventions] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    Promise.all([
      fetchJson('/api/catalog/info'),
      fetchJson('/api/inventory/info'),
      fetchJson('/api/catalog/inventions'),
      fetchJson('/api/inventory/stock'),
    ]).then(([cInfo, iInfo, list, stock]) => {
      setCatalogInfo(cInfo)
      setInventoryInfo(iInfo)
      setInventions(withStock(list ?? [], stock))
      setLoading(false)
    })
  }, [])

  const mismatches = findEnvironmentMismatches(config.environment, [catalogInfo, inventoryInfo])
  const featured = catalogInfo?.featuredEnabled ? inventions.filter((i) => i.featured) : []

  return (
    <>
      <header className={`env-${config.environment}`}>
        <h1>{config.appTitle}</h1>
        <span className="badge">ambiente: {config.environment}</span>
      </header>

      <main>
        {mismatches.length > 0 && (
          <p className="alert">
            ⚠️ El frontend corre en <b>{config.environment}</b>, pero estos servicios reportan otro
            ambiente: {mismatches.join(', ')}.
          </p>
        )}

        <section>
          <h2>Configuración de cada pieza</h2>
          <p className="muted">
            Las mismas imágenes en todos los ambientes. Lo único que cambia es la configuración que
            reciben al arrancar.
          </p>
          <div className="cards">
            <ServiceCard
              title="frontend (nginx)"
              info={config}
              rows={[['ambiente', config.environment], ['origen', '/config.js (runtime)']]}
            />
            <ServiceCard
              title="catalog (Quarkus)"
              info={catalogInfo}
              rows={[
                ['ambiente', catalogInfo?.environment],
                ['destacados', catalogInfo?.featuredEnabled ? 'activado' : 'desactivado'],
                ['log', `${catalogInfo?.logLevel} · ${catalogInfo?.logJson ? 'JSON' : 'texto'}`],
                ['swagger', catalogInfo?.swaggerEnabled ? 'activo' : 'desactivado'],
              ]}
            />
            <ServiceCard
              title="inventory (FastAPI)"
              info={inventoryInfo}
              rows={[
                ['ambiente', inventoryInfo?.environment],
                ['log', `${inventoryInfo?.logLevel} · ${inventoryInfo?.logJson ? 'JSON' : 'texto'}`],
                ['clave proveedor', inventoryInfo?.supplierKeyHint],
              ]}
            />
          </div>
        </section>

        {featured.length > 0 && (
          <section>
            <h2>⭐ Destacados</h2>
            <div className="cards">
              {featured.map((i) => (
                <article className="card featured" key={i.sku}>
                  <h3>{i.name}</h3>
                  <p className="muted">por {i.inventor}</p>
                  <p className="price">{formatPrice(i.price, i.currency)}</p>
                </article>
              ))}
            </div>
          </section>
        )}

        <section>
          <h2>Catálogo</h2>
          {loading ? (
            <p className="muted">Cargando…</p>
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>SKU</th>
                    <th>Invento</th>
                    <th>Inventor</th>
                    <th className="num">Precio</th>
                    <th className="num">Stock</th>
                  </tr>
                </thead>
                <tbody>
                  {inventions.map((i) => (
                    <tr key={i.sku}>
                      <td>{i.sku}</td>
                      <td>{i.name}</td>
                      <td>{i.inventor}</td>
                      <td className="num">{formatPrice(i.price, i.currency)}</td>
                      <td className="num">{i.available ?? '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </main>
    </>
  )
}
