#!/usr/bin/env bash
# Prueba de humo de la comparación aiokafka vs. Quarkus (perfil "quarkus").
#   docker compose --profile quarkus up -d && ./smoke-quarkus.sh
set -euo pipefail
cd "$(dirname "$0")"

ORDERS=http://localhost:8102
PY=http://localhost:8103
QK=http://localhost:8133
fail() { echo "❌ $1"; exit 1; }
ok()   { echo "✅ $1"; }
wait_for() { for _ in $(seq 1 90); do curl -fsS -o /dev/null "$1" 2>/dev/null && return 0; sleep 2; done; fail "$1 no responde"; }
place_order() { # sku qty → id del pedido
  curl -fsS -X POST "$ORDERS/api/orders" -H 'Content-Type: application/json' \
    -d "{\"customerId\":\"ana\",\"items\":[{\"sku\":\"$1\",\"quantity\":$2}]}" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4
}
wait_reserved() { # base order_id [segundos]
  for _ in $(seq 1 "${3:-30}"); do
    curl -fsS "$1/api/reservations?orderId=$2" | grep -q '"status":"RESERVED"' && return 0
    sleep 1
  done
  return 1
}

wait_for "$ORDERS/q/health/ready"; wait_for "$PY/health"; wait_for "$QK/q/health/ready"

id=$(place_order INV-004 1)
wait_reserved "$PY" "$id" || fail "Python no procesó $id"
wait_reserved "$QK" "$id" || fail "Quarkus no procesó $id"
ok "Publish/Subscribe: el MISMO evento lo procesaron los dos grupos (inventory y inventory-quarkus)"

echo "{\"veneno\": $RANDOM" | docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic orders.events >/dev/null
dlq=$(docker compose exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic orders.events.dlq-quarkus --from-beginning --max-messages 1 --timeout-ms 20000 \
  --property print.headers=true 2>/dev/null || true)
grep -q "dead-letter-reason" <<<"$dlq" || fail "Quarkus no mandó el veneno a orders.events.dlq-quarkus"
ok "veneno → orders.events.dlq-quarkus, con el motivo en el header dead-letter-reason (pura configuración)"

# Falla transitoria: evento "notification" (sin ítems) con pedidos CAÍDO cuando el consumidor lo procesa.
EVENT_STYLE=notification docker compose up -d --wait orders >/dev/null 2>&1
docker compose stop inventory-quarkus >/dev/null 2>&1
id=$(place_order INV-001 1)
docker compose stop orders >/dev/null 2>&1
docker compose start inventory-quarkus >/dev/null 2>&1
wait_for "$QK/q/health/live"
sleep 6
wait_reserved "$QK" "$id" 1 && fail "no debería reservar con pedidos caído"
ok "pedidos caído → el consumidor Quarkus reintenta (@Retry) sin perder el evento"
docker compose start orders >/dev/null 2>&1
wait_reserved "$QK" "$id" 60 || fail "al volver pedidos, Quarkus debería reservar $id"
ok "pedidos volvió → el reintento funcionó y el pedido quedó reservado"
docker compose up -d --wait orders >/dev/null 2>&1   # vuelve a EVENT_STYLE=state

echo "🎉 Comparación aiokafka vs. Quarkus OK"
