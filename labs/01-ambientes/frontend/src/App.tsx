import { useEffect, useState } from 'react'
import { loadConfig, findEnvironmentMismatches, type ServiceInfo } from './config'
import { fetchJson, withStock, type Invention, type InventionWithStock, type StockLevel } from './catalog'
import { probePublicApi, type ProbeResult } from './cors'

interface CatalogInfo extends ServiceInfo {
  featuredEnabled: boolean
  logLevel: string
  logJson: boolean
  swaggerEnabled: boolean
}

interface InventoryInfo extends ServiceInfo {
  logLevel: string
  logJson: boolean
  supplierKeyHint: string
}

const config = loadConfig()

function formatPrice(price = 0, currency = 'PEN') {
  return new Intl.NumberFormat('es-PE', { style: 'currency', currency }).format(price)
}

function CorsDemo({ apiUrl }: { apiUrl: string }) {
  const [result, setResult] = useState<ProbeResult | null>(null)
  return (
    <section>
      <h2>🌐 CORS: la API pública</h2>
      <p className="muted">
        En prod el catálogo también se publica en <code>{apiUrl}</code>, un origen distinto de esta página (
        <code>{window.location.origin}</code>). El navegador solo deja leer la respuesta si la API lo autoriza con{' '}
        <code>Access-Control-Allow-Origin</code>. Prueba lo mismo desde otro sitio en{' '}
        <a href="http://localhost:3299">http://localhost:3299</a>.
      </p>
      <button onClick={() => probePublicApi(apiUrl).then(setResult)}>Llamar a la API pública</button>
      {result && <p className={result.ok ? 'ok' : 'alert'}>{result.ok ? '✅' : '⛔'} {result.detail}</p>}
    </section>
  )
}

function ServiceCard({ title, info, rows }: { title: string; info: unknown; rows: [string, unknown][] }) {
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
  const [catalogInfo, setCatalogInfo] = useState<CatalogInfo | null>(null)
  const [inventoryInfo, setInventoryInfo] = useState<InventoryInfo | null>(null)
  const [inventions, setInventions] = useState<InventionWithStock[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    Promise.all([
      fetchJson<CatalogInfo>('/api/catalog/info'),
      fetchJson<InventoryInfo>('/api/inventory/info'),
      fetchJson<Invention[]>('/api/catalog/inventions'),
      fetchJson<StockLevel[]>('/api/inventory/stock'),
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

        {config.publicApiUrl && <CorsDemo apiUrl={config.publicApiUrl} />}

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
