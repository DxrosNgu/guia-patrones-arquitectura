#!/usr/bin/env bash
# Prueba de humo del lab 04: una compra cruza 4 módulos en UNA transacción local.
set -euo pipefail
cd "$(dirname "$0")"

BASE=http://localhost:8100
fail() { echo "❌ $1"; exit 1; }
ok()   { echo "✅ $1"; }
wait_ready() { for _ in $(seq 1 90); do curl -fsS -o /dev/null "$BASE/q/health/ready" 2>/dev/null && return 0; sleep 2; done; fail "shop no está listo"; }

stock() { curl -fsS "$BASE/api/inventory/stock" | grep -o "\"sku\":\"$1\",\"available\":[0-9]*\|\"available\":[0-9]*,\"sku\":\"$1\"" | grep -o '"available":[0-9]*' | cut -d: -f2; }
order() { # cliente sku cantidad → código (cuerpo en /tmp/lab04-body)
  curl -s -o /tmp/lab04-body -w "%{http_code}" -X POST "$BASE/api/orders" -H 'Content-Type: application/json' \
       -d "{\"customerId\":\"$1\",\"items\":[{\"sku\":\"$2\",\"quantity\":$3}]}"
}
order_id() { grep -o '"id":"[^"]*"' /tmp/lab04-body | cut -d'"' -f4; }

wait_ready

before=$(stock INV-004)
[ "$(order ana INV-004 2)" = 201 ] || fail "el pedido feliz no se creó: $(cat /tmp/lab04-body)"
id=$(order_id)
[ "$(stock INV-004)" = $((before - 2)) ] || fail "el stock no bajó"
curl -fsS "$BASE/api/payments" | grep -q "$id" || fail "no hay pago"
curl -fsS "$BASE/api/shipments" | grep -q "$id" || fail "no hay envío"
ok "compra: catálogo → inventario → pagos → pedido, y envíos reaccionó al evento"

before=$(stock INV-003)
[ "$(order beto INV-003 5)" = 402 ] || fail "600 > 500 debería rechazarse: $(cat /tmp/lab04-body)"
[ "$(stock INV-003)" = "$before" ] || fail "el stock quedó reservado tras el rechazo"
ok "pago rechazado → ROLLBACK: el stock de INV-003 sigue en $before (compáralo con el lab 05)"

[ "$(order ana INV-002 1)" = 409 ] || fail "sin stock debería dar 409"
ok "sin stock → 409 OUT_OF_STOCK"

SHIPPING_CHAOS_FAIL=true docker compose up -d --wait shop >/dev/null 2>&1
wait_ready
[ "$(order ana INV-001 1)" = 201 ] || fail "con envíos fallando el pedido igual debería confirmarse"
id=$(order_id)
sleep 1
curl -fsS "$BASE/api/shipments" | grep -q "$id" && fail "no debería haber envío para $id"
ok "envíos falla DESPUÉS del commit → pedido confirmado SIN envío (consistencia eventual, incluso en un monolito)"
docker compose up -d --wait shop >/dev/null 2>&1

echo "🎉 Lab 04 OK"
