// Configuración del frontend EN TIEMPO DE EJECUCIÓN.
//
// ⚠️ Vite reemplaza `import.meta.env.*` al COMPILAR: el valor queda fijo dentro
// del bundle. Si lo usaras para el ambiente, necesitarías una imagen distinta
// por ambiente, y eso rompe "construir una vez, desplegar muchas".
//
// Por eso el ambiente llega en /config.js, un archivo que nginx genera AL
// ARRANCAR el contenedor a partir de variables de entorno
// (ver nginx/40-runtime-config.sh). La misma imagen sirve para local, dev y prod.

export interface AppConfig {
  environment: string
  appTitle: string
  /** URL de la API pública en OTRO origen (solo prod, para la demostración de CORS). Vacío = no hay. */
  publicApiUrl: string
}

/** Lo que responde GET /api/info de cada servicio (solo los campos que usa la UI). */
export interface ServiceInfo {
  service: string
  environment: string
}

declare global {
  interface Window {
    __APP_CONFIG__?: Partial<AppConfig>
  }
}

const DEFAULTS: AppConfig = { environment: 'desconocido', appTitle: 'Mercado de Inventos', publicApiUrl: '' }

export function loadConfig(win: Pick<Window, '__APP_CONFIG__'> = window): AppConfig {
  return { ...DEFAULTS, ...(win.__APP_CONFIG__ ?? {}) }
}

// Si el frontend cree estar en "dev" pero un servicio dice "prod", algo está mal
// cableado. Es un error real y peligroso: este aviso lo hace visible.
export function findEnvironmentMismatches(frontendEnvironment: string,
                                          serviceInfos: (ServiceInfo | null)[]): string[] {
  return serviceInfos
    .filter((info): info is ServiceInfo => info !== null && info.environment !== frontendEnvironment)
    .map((info) => `${info.service} (${info.environment})`)
}
