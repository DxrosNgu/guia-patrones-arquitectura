// Los módulos que exponen los remotos. El shell solo conoce su NOMBRE y su forma (un componente),
// no su código: llegan por la red cuando se usan.
declare module 'catalog/App' {
  import type { ComponentType } from 'react'
  const App: ComponentType
  export default App
}

declare module 'orders/App' {
  import type { ComponentType } from 'react'
  const App: ComponentType
  export default App
}

declare module 'orders/CartBadge' {
  import type { ComponentType } from 'react'
  const CartBadge: ComponentType
  export default CartBadge
}
