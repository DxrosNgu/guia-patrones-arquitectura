export interface Invention {
  sku: string
  name: string
  inventor?: string
  price?: number
  currency?: string
  featured?: boolean
}

export interface StockLevel {
  sku: string
  available: number
}

export type InventionWithStock = Invention & { available: number | null }

// Une el catálogo (servicio catalog) con el stock (servicio inventory).
export function withStock(inventions: Invention[], stock: StockLevel[] | null): InventionWithStock[] {
  const bySku = new Map((stock ?? []).map((item) => [item.sku, item.available]))
  return inventions.map((invention) => ({
    ...invention,
    available: stock ? (bySku.get(invention.sku) ?? null) : null,
  }))
}

// Las llamadas pasan por el mismo origen (/api/...): nginx (o Vite en modo
// desarrollo) las reenvía al servicio correspondiente. Así el navegador nunca
// necesita conocer los puertos internos ni hay problemas de CORS.
export async function fetchJson<T>(path: string): Promise<T | null> {
  try {
    const response = await fetch(path)
    if (!response.ok) return null
    return (await response.json()) as T
  } catch {
    return null
  }
}
