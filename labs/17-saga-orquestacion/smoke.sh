#!/usr/bin/env bash
# Prueba de humo del lab 17: los casos de la matriz, ahora con un orquestador.
# Pensada para un lab recién levantado (docker compose down -v && docker compose up -d).
set -euo pipefail
cd "$(dirname "$0")"

ORDERS=http://localhost:8102
fail() { echo "❌ $1"; exit 1; }
ok()   { echo "✅ $1"; }

wait_url() { for _ in $(seq 1 90); do curl -fsS -o /dev/null "$1" 2>/dev/null && return 0; sleep 2; done; fail "$1 no responde"; }
place() {
  curl -fsS -X POST "$ORDERS/api/orders" -H 'Content-Type: application/json' \
    -d "{\"customerId\":\"smoke\",\"items\":[{\"sku\":\"$1\",\"quantity\":$2,\"unitPrice\":$3}]}" \
    | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4
}
order() { curl -fsS "$ORDERS/api/orders/$1"; }
state() { order "$1" | grep -o '"sagaState":"[A-Z_]*"' | cut -d'"' -f4; }
wait_state() { # id estado [segundos]
  for _ in $(seq 1 "${3:-30}"); do [ "$(state "$1")" = "$2" ] && return 0; sleep 1; done
  fail "la saga $1 debía llegar a $2 y está en $(state "$1")"
}
timeouts() { order "$1" | grep -o '"eventType":"Timeout"' | wc -l | tr -d ' '; }
payments_of() { curl -fsS "http://localhost:8104/api/payments?orderId=$1"; }
stock() { curl -fsS http://localhost:8103/api/stock | grep -o "\"sku\":\"$1\",\"available\":[0-9]*" | grep -o '[0-9]*$'; }
carrier() { curl -fsS -X PUT http://localhost:8105/api/carrier -H 'Content-Type: application/json' -d "{\"mode\":\"$1\"}" >/dev/null; }
payments_delay() { curl -fsS -X PUT http://localhost:8104/api/chaos -H 'Content-Type: application/json' -d "{\"delayMs\":$1}" >/dev/null; }
stop() { docker compose stop "$1" >/dev/null 2>&1; }
start() { docker compose start "$1" >/dev/null 2>&1; docker compose up -d --wait "$1" >/dev/null 2>&1; }

for url in $ORDERS/q/health/ready http://localhost:8104/q/health/ready http://localhost:8103/health http://localhost:8105/health; do wait_url "$url"; done
carrier ok; payments_delay 0

ID=$(place INV-001 1 49.90); wait_state "$ID" APPROVED
ok "S1 camino feliz → APPROVED"

ID=$(place INV-002 1 35); wait_state "$ID" REJECTED
ok "S2 sin stock → REJECTED"

before=$(stock INV-004)
ID=$(place INV-004 10 72.50); wait_state "$ID" REJECTED
[ "$(stock INV-004)" = "$before" ] || fail "S3: el orquestador debía ordenar liberar el stock"
ok "S3 pago rechazado → el orquestador ordena ReleaseStock → REJECTED (stock $before)"

carrier down
ID=$(place INV-001 1 49.90); wait_state "$ID" SCHEDULING_SHIPMENT
for _ in $(seq 1 20); do [ "$(timeouts "$ID")" -ge 1 ] && break; sleep 1; done
[ "$(timeouts "$ID")" -ge 1 ] || fail "S4: el orquestador debía reintentar ScheduleShipment"
carrier ok; wait_state "$ID" APPROVED 40
ok "S4 transportista caído → el orquestador reintenta tras el pivote (sin compensar) → APPROVED"

carrier reject
ID=$(place INV-003 1 189); wait_state "$ID" CANCELLED
payments_of "$ID" | grep -q '"status":"REFUNDED"' || fail "S5: el pago debía quedar REFUNDED"
carrier ok
ok "S5 rechazo definitivo → ReleaseStock + RefundPayment → CANCELLED"

payments_delay 8000
ID=$(place INV-001 1 49.90); wait_state "$ID" CHARGING
for _ in $(seq 1 20); do [ "$(timeouts "$ID")" -ge 1 ] && break; sleep 1; done
[ "$(timeouts "$ID")" -ge 1 ] || fail "S6: el orquestador debía reenviar ChargePayment"
payments_delay 0; wait_state "$ID" APPROVED 60
[ "$(payments_of "$ID" | grep -o '"orderId"' | wc -l | tr -d ' ')" = 1 ] || fail "S6: el reenvío generó otro cobro"
ok "S6 pago lento → timeout → ChargePayment reenviado → pagos reconcilia (1 cobro) → APPROVED"

payments_delay 8000
ID=$(place INV-001 1 49.90); wait_state "$ID" CHARGING
stop orders; sleep 12; start orders
payments_delay 0; wait_state "$ID" APPROVED 60
ok "S10 el orquestador se cae a mitad → al volver retoma desde saga_instance → APPROVED"

payments_delay 8000
before=$(stock INV-001)
ID=$(place INV-001 1 49.90); wait_state "$ID" CHARGING
curl -fsS -X POST "$ORDERS/api/orders/$ID/cancel" >/dev/null
payments_delay 0; wait_state "$ID" CANCELLED 60
[ "$(stock INV-001)" = "$before" ] || fail "S11: el stock debía volver a $before"
ok "S11 cancelación mientras se cobra → ReleaseStock + RefundPayment → CANCELLED"

stop payments
ID=$(place INV-001 1 49.90); wait_state "$ID" CHARGING
wait_state "$ID" COMPENSATING 40
ok "S13 pagos nunca responde → tras 3 intentos el orquestador abandona y compensa"
start payments; wait_state "$ID" EXPIRED 60
ok "S13 al volver pagos, la compensación termina → EXPIRED"

place INV-005 1 120 >/dev/null & place INV-005 1 120 >/dev/null; wait
sleep 10
[ "$(stock INV-005)" -ge 0 ] || fail "S12: stock negativo"
ok "S12 dos sagas por la última unidad → stock $(stock INV-005), nunca negativo"

echo "🎉 Lab 17 OK"
