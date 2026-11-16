#!/usr/bin/env bash
# Prueba de humo del lab 01: comprueba que un ambiente responde con SU configuración.
#
#   ./smoke.sh          # local (http://localhost:3000)
#   ./smoke.sh dev      # dev   (http://localhost:3100)
#   ./smoke.sh prod     # prod  (http://localhost:3200)
set -euo pipefail

ENVIRONMENT="${1:-local}"
case "$ENVIRONMENT" in
  local) PORT=3000; FEATURED=true;  SWAGGER=true  ;;
  dev)   PORT=3100; FEATURED=true;  SWAGGER=true  ;;
  prod)  PORT=3200; FEATURED=false; SWAGGER=false ;;
  *) echo "Uso: $0 [local|dev|prod]"; exit 2 ;;
esac
BASE="http://localhost:$PORT"

fail() { echo "❌ $1"; exit 1; }
ok()   { echo "✅ $1"; }

echo "Esperando a $BASE ..."
for _ in $(seq 1 60); do
  curl -fsS -o /dev/null "$BASE/" 2>/dev/null && break
  sleep 2
done

check() {
  local url="$1" expected="$2" message="$3" body
  body=$(curl -fsS "$url") || fail "$message: $url no responde"
  grep -qF -- "$expected" <<<"$body" || fail "$message: se esperaba '$expected' en $url, llegó: $body"
  ok "$message"
}

check "$BASE/config.js"            "environment: \"$ENVIRONMENT\""     "el frontend sirve el config.js de $ENVIRONMENT"
check "$BASE/api/catalog/info"     "\"environment\":\"$ENVIRONMENT\""  "catalog corre en $ENVIRONMENT"
check "$BASE/api/catalog/info"     "\"featuredEnabled\":$FEATURED"     "flag de destacados = $FEATURED"
check "$BASE/api/catalog/info"     "\"swaggerEnabled\":$SWAGGER"       "Swagger UI activo = $SWAGGER"
check "$BASE/api/inventory/info"   "\"environment\":\"$ENVIRONMENT\""  "inventory corre en $ENVIRONMENT"
check "$BASE/api/catalog/inventions" "INV-004"                         "el catálogo devuelve los inventos de la BD"
check "$BASE/api/inventory/stock"  "INV-001"                           "el inventario devuelve el stock"

echo "🎉 Ambiente $ENVIRONMENT OK"
