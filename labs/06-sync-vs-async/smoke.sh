#!/usr/bin/env bash
# Prueba de humo del lab 06: modo async, inventario caído, mensaje veneno y modo sync.
# Se ejecuta con el lab levantado (docker compose up -d) desde labs/06-sync-vs-async.
set -euo pipefail

ORDERS=http://localhost:8102
INVENTORY=http://localhost:8103

fail() { echo "❌ $1"; exit 1; }
ok()   { echo "✅ $1"; }

wait_for() { # url
  for _ in $(seq 1 90); do curl -fsS -o /dev/null "$1" 2>/dev/null && return 0; sleep 2; done
  fail "$1 no responde"
}
place_order() { # sku qty → imprime "HTTP_CODE BODY"
  curl -sS -o /tmp/lab06-body -w "%{http_code}" -X POST "$ORDERS/api/orders" \
    -H 'Content-Type: application/json' \
    -d "{\"customerId\":\"ana\",\"items\":[{\"sku\":\"$1\",\"quantity\":$2}]}"
  echo " $(cat /tmp/lab06-body)"
}
order_id() { grep -o '"id":"[^"]*"' <<<"$1" | head -1 | cut -d'"' -f4; }
wait_processed() { # order_id
  for _ in $(seq 1 30); do
    curl -fsS "$INVENTORY/api/reservations" | grep -q "\"orderId\":\"$1\"" && return 0
    sleep 1
  done
  fail "inventario no procesó el pedido $1"
}

wait_for "$ORDERS/q/health/ready"
wait_for "$INVENTORY/health"

# 1 ─ Async: el pedido se acepta y el evento se procesa después.
res=$(place_order INV-001 1)
[[ $res == 202* ]] || fail "async: se esperaba 202, llegó: $res"
ok "async: pedido aceptado con 202 (sin esperar a inventario)"
wait_processed "$(order_id "$res")"
ok "async: inventario procesó el evento OrderPlaced"

# 2 ─ Async con inventario caído: se sigue vendiendo.
docker compose stop inventory >/dev/null 2>&1
res=$(place_order INV-004 1)
[[ $res == 202* ]] || fail "async con inventario caído: se esperaba 202, llegó: $res"
ok "async: con inventario CAÍDO el pedido igual se acepta"
docker compose start inventory >/dev/null 2>&1
wait_for "$INVENTORY/health"
wait_processed "$(order_id "$res")"
ok "async: al volver, inventario se puso al día con el evento pendiente"

# 3 ─ Mensaje veneno → DLQ, y el consumidor sigue funcionando.
echo "esto no es json" | docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic orders.events >/dev/null
dlq=$(docker compose exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic orders.events.dlq --from-beginning --max-messages 1 --timeout-ms 20000 2>/dev/null || true)
grep -q "esto no es json" <<<"$dlq" || fail "el mensaje veneno no llegó a la DLQ"
ok "mensaje veneno enviado a orders.events.dlq"
res=$(place_order INV-001 1)
wait_processed "$(order_id "$res")"
ok "el consumidor sigue procesando después del veneno"

# 4 ─ Sync: con inventario caído, el pedido FALLA.
ORDERS_MODE=sync docker compose up -d --wait orders >/dev/null 2>&1
res=$(place_order INV-001 1)
[[ $res == 201*CONFIRMED* ]] || fail "sync: se esperaba 201 CONFIRMED, llegó: $res"
ok "sync: pedido confirmado en la misma llamada (201)"
docker compose stop inventory >/dev/null 2>&1
res=$(place_order INV-001 1)
[[ $res == 503* ]] || fail "sync con inventario caído: se esperaba 503, llegó: $res"
ok "sync: con inventario CAÍDO no se puede comprar (503)"
docker compose start inventory >/dev/null 2>&1
docker compose up -d --wait orders >/dev/null 2>&1   # vuelve a async

echo "🎉 Lab 06 OK"
