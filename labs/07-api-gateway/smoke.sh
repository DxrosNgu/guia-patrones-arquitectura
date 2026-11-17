#!/usr/bin/env bash
# Prueba de humo del lab 07: rutas, autenticación, autorización, rate limiting,
# defensa en profundidad y qué pasa cuando cae un servicio.
set -euo pipefail
cd "$(dirname "$0")"

GW=http://localhost:8080
fail() { echo "❌ $1"; exit 1; }
ok()   { echo "✅ $1"; }
wait_for() { for _ in $(seq 1 90); do curl -fsS -o /dev/null "$1" 2>/dev/null && return 0; sleep 2; done; fail "$1 no responde"; }
code() { curl -s -o /tmp/lab07-body -w "%{http_code}" "$@"; }
ORDER='{"items":[{"sku":"INV-001","quantity":1}]}'

wait_for "$GW/actuator/health"

[ "$(code $GW/api/catalog/inventions)" = 200 ] || fail "el catálogo debería ser público"
ok "GET /api/catalog/inventions sin token → 200 (público, ruteado a catalog)"

[ "$(code $GW/api/orders)" = 401 ] || fail "pedidos sin token debería dar 401"
ok "GET /api/orders sin token → 401"

ANA=$(./token.sh ana); BETO=$(./token.sh beto); CARLA=$(./token.sh carla)
[ "$(code -X POST $GW/api/orders -H "Authorization: Bearer $BETO" -H 'Content-Type: application/json' -d "$ORDER")" = 403 ] \
  || fail "beto (sin rol buyer) no debería poder comprar"
ok "POST /api/orders con token de beto (sin rol buyer) → 403"

[ "$(code -X POST $GW/api/orders -H "Authorization: Bearer $ANA" -H 'Content-Type: application/json' \
      -d '{"customerId":"beto","items":[{"sku":"INV-001","quantity":1}]}')" = 201 ] || fail "ana debería poder comprar"
grep -q '"customer":"ana"' /tmp/lab07-body || fail "el pedido debería quedar a nombre de ana (del token), no de beto"
ok "POST /api/orders con token de ana → 201, a nombre de ana aunque el cuerpo diga beto"

[ "$(code -X POST localhost:8102/api/orders -H 'Content-Type: application/json' -d "$ORDER")" = 401 ] \
  || fail "llamar a orders directo, sin token, debería dar 401"
[ "$(code -X POST localhost:8102/api/orders -H "Authorization: Bearer $BETO" -H 'Content-Type: application/json' -d "$ORDER")" = 403 ] \
  || fail "llamar a orders directo con el token de beto debería dar 403"
ok "saltándose el gateway (:8102 directo), orders igual exige token (401) y rol (403): defensa en profundidad"

statuses=$(for _ in $(seq 1 12); do code $GW/api/orders -H "Authorization: Bearer $CARLA"; echo; done | sort | uniq -c | tr -s ' ' | tr '\n' ' ')
grep -q " 429" <<<"$statuses" || fail "una ráfaga de carla debería recibir 429 (llegó: $statuses)"
[ "$(code $GW/api/orders -H "Authorization: Bearer $ANA")" = 200 ] || fail "ana no debería verse afectada por la ráfaga de carla"
ok "rate limiting: ráfaga de 12 de carla → [$statuses]; ana sigue con 200 (un balde por usuario)"

pre=$(curl -s -o /dev/null -D - -X OPTIONS $GW/api/orders -H 'Origin: http://localhost:3000' \
        -H 'Access-Control-Request-Method: POST' | tr -d '\r' | grep -i '^access-control-allow-origin' || true)
[ -n "$pre" ] || fail "el preflight CORS desde localhost:3000 debería estar permitido"
ok "CORS se resuelve en el gateway: preflight desde localhost:3000 permitido"

docker compose stop catalog >/dev/null 2>&1
down=$(code $GW/api/catalog/inventions)
sleep 1   # el balde de ana se rellena
orders_ok=$(code $GW/api/orders -H "Authorization: Bearer $ANA")
docker compose start catalog >/dev/null 2>&1
[[ $down == 5* ]] || fail "con catalog caído el gateway debería responder 5xx (llegó $down)"
[ "$orders_ok" = 200 ] || fail "con catalog caído, la ruta de orders debería seguir funcionando (llegó $orders_ok)"
ok "catalog caído → /api/catalog responde $down, pero /api/orders sigue con 200"
wait_for "$GW/api/catalog/inventions"

echo "🎉 Lab 07 OK"
