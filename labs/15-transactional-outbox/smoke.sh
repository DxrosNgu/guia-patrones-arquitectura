#!/usr/bin/env bash
# Prueba de humo del lab 15: polling, caída después del commit (outbox vs dual write) y CDC.
set -euo pipefail
cd "$(dirname "$0")"

ORDERS=http://localhost:8102
RUN=$(date +%s)
fail() { echo "❌ $1"; exit 1; }
ok()   { echo "✅ $1"; }

wait_ready() { for _ in $(seq 1 90); do curl -fsS -o /dev/null "$ORDERS/q/health/ready" 2>/dev/null && return 0; sleep 2; done; fail "orders no está listo"; }
place() {
  local code
  code=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$ORDERS/api/orders" -H 'Content-Type: application/json' \
         -d "{\"customerId\":\"$1\",\"items\":[{\"sku\":\"INV-001\",\"quantity\":1}]}") || true
  echo "${code:-000}"
}
order_id_of() { curl -fsS "$ORDERS/api/orders" | grep -o "\"id\":\"[^\"]*\",\"customerId\":\"$1\"" | cut -d'"' -f4; }
kafka_events() {
  docker compose exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
    --topic orders.events --from-beginning --timeout-ms 6000 \
    --property print.key=true --property print.headers=true 2>/dev/null || true
}
wait_event() { for _ in $(seq 1 15); do kafka_events | grep -q "$1" && return 0; sleep 2; done; return 1; }

wait_ready

# 1 ─ Polling: el pedido se guarda con su evento y el publicador lo envía.
[ "$(place "poll-$RUN")" = 202 ] || fail "el pedido no fue aceptado"
ID=$(order_id_of "poll-$RUN")
wait_event "$ID" || fail "el evento del pedido $ID no llegó a Kafka"
ok "polling: pedido $ID → outbox → Kafka"

# 2 ─ Outbox + caída justo después del commit: el evento NO se pierde.
CHAOS_CRASH_AFTER_COMMIT=true docker compose up -d --wait orders >/dev/null 2>&1
code=$(place "chaos-outbox-$RUN")
ok "caos: la petición se cortó (HTTP $code) porque el proceso murió después del commit"
sleep 5; wait_ready
ID=$(order_id_of "chaos-outbox-$RUN")
[ -n "$ID" ] || fail "el pedido debía existir en la base"
wait_event "$ID" || fail "con outbox, el evento de $ID debía publicarse al reiniciar"
ok "outbox: tras reiniciar, el publicador encontró el evento pendiente y lo publicó"

# 3 ─ Dual write + la misma caída: el evento se PIERDE.
OUTBOX_MODE=naive CHAOS_CRASH_AFTER_COMMIT=true docker compose up -d --wait --force-recreate orders >/dev/null 2>&1
place "chaos-naive-$RUN" >/dev/null
sleep 5; wait_ready
ID=$(order_id_of "chaos-naive-$RUN")
[ -n "$ID" ] || fail "el pedido ingenuo debía existir en la base"
if wait_event "$ID"; then fail "en modo naive el evento de $ID NO debía existir"; fi
ok "dual write: el pedido $ID quedó en la base SIN evento (perdido para siempre)"

# 4 ─ CDC: Debezium lee el WAL y publica, sin que pedidos toque Kafka.
OUTBOX_MODE=cdc docker compose --profile cdc up -d --wait --force-recreate orders connect >/dev/null 2>&1
OUTBOX_MODE=cdc docker compose --profile cdc up connect-init >/dev/null 2>&1
sleep 5
[ "$(place "cdc-$RUN")" = 202 ] || fail "el pedido cdc no fue aceptado"
ID=$(order_id_of "cdc-$RUN")
wait_event "$ID" || fail "Debezium no publicó el evento de $ID"
kafka_events | grep "$ID" | grep -q "eventType:OrderPlaced" || fail "falta el header eventType que agrega el Outbox Event Router"
ok "cdc: Debezium publicó el evento de $ID (con header eventType)"

docker compose --profile cdc stop connect >/dev/null 2>&1
docker compose up -d --wait --force-recreate orders >/dev/null 2>&1   # vuelve a polling
echo "🎉 Lab 15 OK"
