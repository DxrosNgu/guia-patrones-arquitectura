#!/usr/bin/env bash
# Prueba de humo del lab 09: composición, paralelo vs. cascada, timeout y respuestas parciales.
set -euo pipefail
cd "$(dirname "$0")"

WEB=http://localhost:3000
fail() { echo "❌ $1"; exit 1; }
ok()   { echo "✅ $1"; }
wait_for() { for _ in $(seq 1 90); do curl -fsS -o /dev/null "$1" 2>/dev/null && return 0; sleep 2; done; fail "$1 no responde"; }
screen() { curl -s -w '\n%{http_code}' "$WEB/bff/my-orders?customer=ana&mode=${1:-parallel}"; }
field() { python3 -c "import json,sys; d=json.load(sys.stdin); print($1)"; }
delay() { curl -fsS -o /dev/null -X PUT "http://localhost:$1/admin/delay" -H 'Content-Type: application/json' -d "{\"ms\":$2}"; }
restore() { delay 8104 0 || true; delay 8105 0 || true; docker compose start shipping orders >/dev/null 2>&1 || true; }
trap restore EXIT

wait_for "$WEB/"
wait_for "$WEB/bff/my-orders?customer=ana"

out=$(screen); body=$(head -1 <<<"$out")
[ "$(field 'len(d["orders"])' <<<"$body")" = 3 ] && [ "$(field 'd["unavailable"]' <<<"$body")" = "[]" ] \
  || fail "la pantalla de ana debería tener 3 pedidos completos: $body"
[ "$(field 'd["orders"][1]["shipment"]' <<<"$body")" = "En camino" ] || fail "el BFF debería traducir IN_TRANSIT a 'En camino'"
ok "1 petición del navegador → el BFF compone 3 servicios: $(field '[(o["id"], o["payment"], o["shipment"]) for o in d["orders"]]' <<<"$body")"

delay 8104 500; delay 8105 500
seq_ms=$(screen sequential | head -1 | field 'd["tookMs"]')
par_ms=$(screen parallel | head -1 | field 'd["tookMs"]')
[ "$seq_ms" -ge 1000 ] && [ "$par_ms" -lt 900 ] || fail "con pagos y envíos a 500 ms: cascada=$seq_ms ms, paralelo=$par_ms ms"
ok "pagos y envíos tardan 500 ms cada uno → en cascada $seq_ms ms, en paralelo $par_ms ms"
delay 8104 0; delay 8105 0

docker compose stop shipping >/dev/null 2>&1
out=$(screen); body=$(head -1 <<<"$out"); code=$(tail -1 <<<"$out")
[ "$code" = 200 ] && [ "$(field 'd["unavailable"]' <<<"$body")" = "['shipping']" ] || fail "con envíos caído: HTTP $code $body"
[ "$(field 'd["orders"][0]["payment"]' <<<"$body")" = "Pagado" ] || fail "los pagos deberían seguir llegando"
ok "envíos caído → 200 con respuesta PARCIAL: unavailable=['shipping'], los pagos siguen ahí"
docker compose start shipping >/dev/null 2>&1
wait_for http://localhost:8105/health

delay 8104 3000
start=$(date +%s%N)
body=$(screen | head -1)
took=$(( ($(date +%s%N) - start) / 1000000 ))
[ "$(field 'd["unavailable"]' <<<"$body")" = "['payments']" ] && [ "$took" -lt 2000 ] \
  || fail "pagos a 3 s debería cortarse por timeout (1 s): tardó $took ms, $body"
ok "pagos tarda 3 s → el BFF lo corta al segundo: la pantalla llega en $took ms, sin el estado de pago"
delay 8104 0

docker compose stop orders >/dev/null 2>&1
[ "$(screen | tail -1)" = 503 ] || fail "sin pedidos no hay pantalla: debería ser 503"
ok "pedidos caído → 503: sin la lista de pedidos no hay nada que componer"
docker compose start orders >/dev/null 2>&1
wait_for http://localhost:8102/q/health/ready

echo "🎉 Lab 09 OK — abre $WEB"
