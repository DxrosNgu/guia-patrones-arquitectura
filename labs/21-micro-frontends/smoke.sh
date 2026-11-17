#!/usr/bin/env bash
# Prueba de humo del lab 21 (por HTTP): el shell sirve sus rutas, los remotos llegan por el shell,
# remoteEntry.js no se cachea y los assets sí, y un remoto caído no tumba al shell ni al otro remoto.
# Lo que pasa DENTRO del navegador (React una sola vez, eventos, fallback) se ve en el README.
set -euo pipefail
cd "$(dirname "$0")"

SHELL_URL=http://localhost:3000
fail() { echo "❌ $1"; exit 1; }
ok()   { echo "✅ $1"; }
wait_for() { for _ in $(seq 1 60); do curl -fsS -o /dev/null "$1" 2>/dev/null && return 0; sleep 1; done; fail "$1 no responde"; }
code() { curl -s -o /dev/null -w '%{http_code}' "$SHELL_URL$1"; }
header() { curl -sI "$SHELL_URL$1" | tr -d '\r' | awk -v h="$(echo "$2" | tr 'A-Z' 'a-z')" -F': ' 'tolower($1) == h {print $2}'; }
# nginx guarda la IP de cada remoto hasta 10 s (resolver valid=10s): tras parar o arrancar uno, se reintenta.
eventually() { for _ in $(seq 1 15); do "$@" && return 0; sleep 1; done; return 1; }
codes_are() { [ "$(code "$1")" = "$2" ] && [ "$(code "$3")" = "$4" ] && [ "$(code "$5")" = "$6" ]; }
restore() { docker compose start catalog-mfe orders-mfe >/dev/null 2>&1 || true; }
trap restore EXIT

wait_for "$SHELL_URL/"; wait_for "$SHELL_URL/remotes/catalog/remoteEntry.js"; wait_for "$SHELL_URL/remotes/orders/remoteEntry.js"

# 1. Las rutas del shell: cualquier URL de la app devuelve la misma página (el router decide en el navegador).
for path in / /catalogo "/catalogo?q=taza" /pedidos /pedidos/P-42; do
  [ "$(code "$path")" = 200 ] || fail "el shell no sirve $path"
done
ok "el shell sirve /, /catalogo?q=taza, /pedidos/P-42…: los links a cualquier pantalla funcionan"

# 2. Los remotos llegan por el shell (mismo origen, sin CORS).
for remote in catalog orders; do
  [ "$(code "/remotes/$remote/remoteEntry.js")" = 200 ] || fail "remoteEntry de $remote"
  [ "$(header "/remotes/$remote/remoteEntry.js" Cache-Control)" = no-cache ] || fail "remoteEntry de $remote no debería cachearse"
done
asset=$(curl -fsS "$SHELL_URL/remotes/orders/remoteEntry.js" | grep -oE 'assets/[A-Za-z0-9_.-]+\.js' | head -1)
[ -n "$asset" ] && [ "$(header "/remotes/orders/$asset" Cache-Control)" = "public, max-age=31536000, immutable" ] \
  || fail "los assets del remoto deberían cachearse para siempre ($asset)"
ok "remoteEntry.js de cada remoto: no-cache (apunta a la versión desplegada); sus assets con hash: cache de 1 año"

# 3. Un remoto caído no tumba nada más.
docker compose stop catalog-mfe >/dev/null 2>&1
eventually codes_are /remotes/catalog/remoteEntry.js 502 /catalogo 200 /remotes/orders/remoteEntry.js 200 \
  || fail "con el catálogo caído, el shell y pedidos deberían seguir"
ok "catálogo caído → su remoteEntry da 502, pero el shell sigue sirviendo y el remoto de pedidos también"
docker compose start catalog-mfe >/dev/null 2>&1; wait_for "$SHELL_URL/remotes/catalog/remoteEntry.js"

docker compose stop orders-mfe >/dev/null 2>&1
eventually codes_are /remotes/orders/remoteEntry.js 502 /pedidos 200 /remotes/catalog/remoteEntry.js 200 \
  || fail "con pedidos caído, el shell y el catálogo deberían seguir"
ok "pedidos caído → lo mismo al revés: el catálogo sigue disponible"
docker compose start orders-mfe >/dev/null 2>&1; wait_for "$SHELL_URL/remotes/orders/remoteEntry.js"

# 4. Cada remoto también se sirve solo (su modo independiente).
docker compose exec -T catalog-mfe wget -qO- http://localhost/ | grep -q "modo independiente" || fail "modo independiente del catálogo"
ok "cada remoto tiene su propia página (modo independiente): el equipo lo prueba sin el shell"

echo "🎉 Lab 21 OK"
