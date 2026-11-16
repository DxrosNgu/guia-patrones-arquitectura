// Configuración del frontend EN TIEMPO DE EJECUCIÓN.
//
// ⚠️ Vite reemplaza `import.meta.env.*` al COMPILAR: el valor queda fijo dentro
// del bundle. Si lo usaras para el ambiente, necesitarías una imagen distinta
// por ambiente, y eso rompe "construir una vez, desplegar muchas".
//
// Por eso el ambiente llega en /config.js, un archivo que nginx genera AL
// ARRANCAR el contenedor a partir de variables de entorno
// (ver nginx/40-runtime-config.sh). La misma imagen sirve para local, dev y prod.

const DEFAULTS = { environment: 'desconocido', appTitle: 'Mercado de Inventos' }

export function loadConfig(win = window) {
  return { ...DEFAULTS, ...(win.__APP_CONFIG__ ?? {}) }
}

// Si el frontend cree estar en "dev" pero un servicio dice "prod", algo está mal
// cableado. Es un error real y peligroso: este aviso lo hace visible.
export function findEnvironmentMismatches(frontendEnvironment, serviceInfos) {
  return serviceInfos
    .filter((info) => info && info.environment !== frontendEnvironment)
    .map((info) => `${info.service} (${info.environment})`)
}
