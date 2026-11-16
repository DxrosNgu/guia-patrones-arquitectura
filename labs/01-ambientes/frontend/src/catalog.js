// Une el catálogo (servicio catalog) con el stock (servicio inventory).
export function withStock(inventions, stock) {
  const bySku = new Map((stock ?? []).map((item) => [item.sku, item.available]))
  return inventions.map((invention) => ({
    ...invention,
    available: stock ? (bySku.get(invention.sku) ?? null) : null,
  }))
}

// Las llamadas pasan por el mismo origen (/api/...): nginx (o Vite en modo
// desarrollo) las reenvía al servicio correspondiente. Así el navegador nunca
// necesita conocer los puertos internos ni hay problemas de CORS.
export async function fetchJson(path) {
  try {
    const response = await fetch(path)
    if (!response.ok) return null
    return await response.json()
  } catch {
    return null
  }
}
