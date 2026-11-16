#!/usr/bin/env bash
# Prueba de humo del ambiente PROD opcional (Kafka con 3 nodos).
#   docker compose -f compose.yaml -f compose.prod.yaml up -d && ./smoke-prod.sh
set -euo pipefail
cd "$(dirname "$0")"

P="docker compose -f compose.yaml -f compose.prod.yaml"
ORDERS=http://localhost:8202
INVENTORY=http://localhost:8203
fail() { echo "❌ $1"; exit 1; }
ok()   { echo "✅ $1"; }
wait_for() { for _ in $(seq 1 90); do curl -fsS -o /dev/null "$1" 2>/dev/null && return 0; sleep 2; done; fail "$1 no responde"; }
order() { curl -s -o /dev/null -m 30 -w "%{http_code}" -X POST "$ORDERS/api/orders" -H 'Content-Type: application/json' \
            -d '{"customerId":"ana","items":[{"sku":"INV-004","quantity":1}]}'; }
all_reserved() { # cuántos pedidos NO tienen reserva todavía
  python3 -c "
import json, urllib.request
orders = json.load(urllib.request.urlopen('$ORDERS/api/orders'))
res = {r['orderId'] for r in json.load(urllib.request.urlopen('$INVENTORY/api/reservations'))}
print(sum(o['id'] not in res for o in orders))"
}
restore() { $P start kafka-2 kafka-3 >/dev/null 2>&1 || true; }
trap restore EXIT

wait_for "$ORDERS/q/health/ready"; wait_for "$INVENTORY/health"

desc=$($P exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --describe --topic orders.events)
grep -q "ReplicationFactor: 3" <<<"$desc" && grep -q "min.insync.replicas=2" <<<"$desc" \
  || fail "orders.events debería tener replication-factor 3 y min.insync.replicas=2"
ok "orders.events: 3 particiones × 3 réplicas, min.insync.replicas=2"

[ "$(order)" = 202 ] || fail "con 3 nodos el pedido debería aceptarse"
ok "3 nodos: pedido aceptado"

$P stop kafka-3 >/dev/null 2>&1
[ "$(order)" = 202 ] || fail "con 2 nodos el pedido debería aceptarse"
ok "2 nodos (cae kafka-3): pedido aceptado; quórum 2 de 3 y 2 copias ≥ min.insync.replicas"

$P stop kafka-2 >/dev/null 2>&1
code=$(order)
[ "$code" != 202 ] || fail "con 1 nodo y acks=all el pedido NO debería aceptarse"
ok "1 nodo: pedido rechazado (HTTP $code) → NOT_ENOUGH_REPLICAS: no hay 2 copias"

restore
for _ in $(seq 1 60); do [ "$(all_reserved)" = 0 ] && break; sleep 2; done
[ "$(all_reserved)" = 0 ] || fail "al volver los nodos, todos los pedidos deberían tener reserva"
ok "nodos de vuelta: TODOS los pedidos quedaron reservados, incluso el que respondió $code (ver README)"

echo "🎉 Lab 06 prod (Kafka con 3 nodos) OK"
