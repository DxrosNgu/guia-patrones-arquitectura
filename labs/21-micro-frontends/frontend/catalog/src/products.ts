export interface Product {
  sku: string
  name: string
  description: string
  price: number
}

// En un sistema real, el remoto los pide al backend de SU equipo (lab 09: un BFF por interfaz).
export const PRODUCTS: Product[] = [
  { sku: 'INV-001', name: 'Paraguas solar', description: 'Te protege de la lluvia y carga tu celular.', price: 49.9 },
  { sku: 'INV-002', name: 'Lámpara de agua salada', description: 'Una cucharada de sal, 8 horas de luz.', price: 89 },
  { sku: 'INV-003', name: 'Mochila con ventilador', description: 'Espalda seca en el verano de Lima.', price: 120 },
  { sku: 'INV-004', name: 'Taza que avisa cuando se enfría', description: 'Vibra antes de que el café llegue a 40 °C.', price: 25.5 },
  { sku: 'INV-005', name: 'Zapatillas autolimpiantes', description: 'Repelen el barro con un recubrimiento hidrofóbico.', price: 159 },
  { sku: 'INV-006', name: 'Maceta que pide agua', description: 'Un sensor de humedad y una luz roja.', price: 39.9 },
]

const plain = (text: string) => text.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()

/** Búsqueda sin tildes ni mayúsculas: "lampara" encuentra "Lámpara". */
export function filterProducts(products: Product[], query: string): Product[] {
  const q = plain(query.trim())
  return q ? products.filter((p) => plain(`${p.name} ${p.description}`).includes(q)) : products
}
