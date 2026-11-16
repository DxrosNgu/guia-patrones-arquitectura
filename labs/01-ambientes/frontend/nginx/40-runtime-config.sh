#!/bin/sh
# La imagen oficial de nginx ejecuta los scripts de /docker-entrypoint.d/ antes
# de arrancar. Este genera /config.js con las variables de entorno del
# contenedor: así el MISMO bundle sirve para cualquier ambiente.
set -eu

cat > /usr/share/nginx/html/config.js <<EOF
window.__APP_CONFIG__ = {
  environment: "${APP_ENV:-desconocido}",
  appTitle: "${APP_TITLE:-Mercado de Inventos}",
  publicApiUrl: "${PUBLIC_API_URL:-}"
};
EOF

echo "config.js generado para el ambiente '${APP_ENV:-desconocido}'"
