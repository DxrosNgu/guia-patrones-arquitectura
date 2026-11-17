/**
 * La ÚNICA instancia del carrito en el navegador. App y CartBadge son dos módulos expuestos del MISMO remoto:
 * Module Federation carga este archivo una vez y los dos lo comparten.
 */
import { useSyncExternalStore } from 'react'
import { Cart } from './cart'

export const cart = new Cart(window.sessionStorage, window.localStorage)
cart.listen()

export const useCartLines = () => useSyncExternalStore(cart.subscribe, cart.getLines)
export const useHistory = () => useSyncExternalStore(cart.subscribe, cart.getHistory)

// La versión de este remoto. Se fija al construir la imagen (ORDERS_VERSION): sirve para ver,
// en el experimento de despliegue, que el shell carga la versión nueva sin reconstruirse.
export const VERSION = import.meta.env.VITE_ORDERS_VERSION ?? '1'
