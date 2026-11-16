#!/usr/bin/env bash
# Prueba de humo del lab 16: reproduce los casos de la matriz de la saga.
# Pensada para un lab recién levantado (docker compose down -v && docker compose up -d).
set -euo pipefail
cd "$(dirname "$0")"

ORDERS=http://localhost:8102
fail() { echo "❌ $1"; exit 1; }
ok()   { echo "✅ $1"; }

wait_url() { for _ in $(seq 1 90); do curl -fsS -o /dev/null "$1" 2>/dev/null && return 0; sleep 2; done; fail "$1 no responde"; }
place() { # sku qty precio → id
  curl -fsS -X POST "$ORDERS/api/orders" -H 'Content-Type: application/json' \
    -d "{\"customerId\":\"smoke\",\"items\":[{\"sku\":\"$1\",\"quantity\":$2,\"unitPrice\":$3}]}" \
    | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4
}
order() { curl -fsS "$ORDERS/api/orders/$1"; }
status() { order "$1" | grep -o '"status":"[A-Z_]*"' | head -1 | cut -d'"' -f4; }
wait_status() { # id estado [segundos]
  for _ in $(seq 1 "${3:-30}"); do [ "$(status "$1")" = "$2" ] && return 0; sleep 1; done
  fail "el pedido $1 debía llegar a $2 y está en $(status "$1")"
}
has_event() { order "$1" | grep -q "\"eventType\":\"$2\""; }
wait_event() { for _ in $(seq 1 30); do has_event "$1" "$2" && return 0; sleep 1; done; fail "falta $2 en la línea de tiempo de $1"; }
stock() { curl -fsS http://localhost:8103/api/stock | grep -o "\"sku\":\"$1\",\"available\":[0-9]*" | grep -o '[0-9]*$'; }
carrier() { curl -fsS -X PUT http://localhost:8105/api/carrier -H 'Content-Type: application/json' -d "{\"mode\":\"$1\"}" >/dev/null; }
stop() { docker compose stop "$1" >/dev/null 2>&1; }
start() { docker compose start "$1" >/dev/null 2>&1; docker compose up -d --wait "$1" >/dev/null 2>&1; }

for url in $ORDERS/q/health/ready http://localhost:8104/q/health/ready http://localhost:8103/health http://localhost:8105/health; do wait_url "$url"; done
carrier ok

# S1 · camino feliz
ID=$(place INV-001 1 49.90); wait_status "$ID" APPROVED
ok "S1 camino feliz → APPROVED"

# S2 · sin stock
ID=$(place INV-002 1 35); wait_status "$ID" REJECTED
ok "S2 sin stock → REJECTED (nada que compensar)"

# S3 · pago rechazado → inventario compensa
before=$(stock INV-004)
ID=$(place INV-004 10 72.50); wait_status "$ID" REJECTED; wait_event "$ID" StockReleased
[ "$(stock INV-004)" = "$before" ] || fail "S3: el stock debía volver a $before"
ok "S3 pago rechazado → REJECTED + StockReleased (stock vuelve a $before)"

# S4 · transportista caído DESPUÉS del pivote → se reintenta, no se compensa
carrier down
ID=$(place INV-001 1 49.90); wait_status "$ID" PAID
sleep 4; [ "$(status "$ID")" = PAID ] || fail "S4: debía quedarse en PAID mientras el transportista está caído"
carrier ok; wait_status "$ID" APPROVED
ok "S4 transportista caído → espera en PAID, reintenta y termina APPROVED"

# S5 · el transportista rechaza para siempre → compensación posterior al pivote
carrier reject
ID=$(place INV-003 1 189); wait_status "$ID" CANCELLED; wait_event "$ID" PaymentRefunded; wait_event "$ID" StockReleased
carrier ok
ok "S5 rechazo definitivo → CANCELLED + PaymentRefunded + StockReleased"

# S7 · mensaje duplicado → un solo cobro
ID=$(place INV-001 1 49.90); wait_status "$ID" APPROVED
scripts/replay.sh inventory.events "$ID" StockReserved >/dev/null; sleep 4
[ "$(curl -fsS "http://localhost:8104/api/payments?orderId=$ID" | grep -o '"orderId"' | wc -l | tr -d ' ')" = 1 ] \
  || fail "S7: el StockReserved duplicado generó otro cobro"
ok "S7 StockReserved reentregado → pagos lo ignora (1 solo cobro)"

# S10 + S8 · un participante caído a mitad de la saga, y un evento fuera de orden
stop payments
ID=$(place INV-001 1 49.90); wait_status "$ID" STOCK_RESERVED
scripts/publish.sh shipping.events ShipmentScheduled "$ID" '{"trackingCode":"TRK-FALSO"}' >/dev/null
for _ in $(seq 1 20); do order "$ID" | grep -q '"applied":false' && break; sleep 1; done
order "$ID" | grep -q '"applied":false' || fail "S8: el ShipmentScheduled fuera de orden debía ignorarse"
ok "S8 ShipmentScheduled antes de tiempo → ignorado (máquina de estados)"
start payments; wait_status "$ID" APPROVED
ok "S10 pagos caído → la saga espera en Kafka y sigue al volver → APPROVED"

# S11 · el cliente cancela durante la saga
stop payments
before=$(stock INV-001)
ID=$(place INV-001 1 49.90); wait_status "$ID" STOCK_RESERVED
curl -fsS -X POST "$ORDERS/api/orders/$ID/cancel" >/dev/null; wait_event "$ID" StockReleased
start payments; sleep 4
[ "$(status "$ID")" = CANCELLED ] || fail "S11: debía quedar CANCELLED"
[ "$(stock INV-001)" = "$before" ] || fail "S11: el stock debía volver a $before"
ok "S11 cancelación antes del pivote → CANCELLED, stock liberado, sin cobro"

# S9 · la compensación espera a que el participante vuelva
stop payments
before=$(stock INV-004)
ID=$(place INV-004 10 72.50); wait_status "$ID" STOCK_RESERVED
stop inventory; start payments; wait_status "$ID" REJECTED
sleep 3; has_event "$ID" StockReleased && fail "S9: inventario está caído, no pudo compensar todavía"
start inventory; wait_event "$ID" StockReleased
[ "$(stock INV-004)" = "$before" ] || fail "S9: el stock debía volver a $before"
ok "S9 compensación con inventario caído → se aplica al volver (stock $before)"

# S12 · dos sagas compiten por la última unidad de INV-005
place INV-005 1 120 >/dev/null & place INV-005 1 120 >/dev/null; wait
sleep 8
approved=$(curl -fsS "$ORDERS/api/orders" | grep -o '"status":"APPROVED"[^}]*"sku":"INV-005"' | wc -l | tr -d ' ')
[ "$(stock INV-005)" -ge 0 ] || fail "S12: stock negativo"
[ "$approved" -le 1 ] || fail "S12: dos pedidos se llevaron la misma unidad"
ok "S12 dos pedidos por la última unidad → como mucho uno aprobado (stock $(stock INV-005))"

echo "🎉 Lab 16 OK"
