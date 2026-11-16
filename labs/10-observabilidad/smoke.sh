#!/usr/bin/env bash
# Prueba de humo del lab 10: un pedido produce UNA traza con spans de los dos
# servicios, logs correlacionados por traceId y una métrica de negocio.
set -euo pipefail

fail() { echo "❌ $1"; exit 1; }
ok()   { echo "✅ $1"; }
lgtm() { docker compose exec -T lgtm curl -s "$@"; }

for _ in $(seq 1 90); do
  curl -fsS -o /dev/null http://localhost:8103/health/ready 2>/dev/null \
    && curl -fsS -o /dev/null http://localhost:8102/q/health/ready 2>/dev/null && break
  sleep 2
done

headers=$(curl -sS -D - -o /dev/null -X POST http://localhost:8102/api/orders -H 'Content-Type: application/json' \
  -d '{"customerId":"ana","items":[{"sku":"INV-001","quantity":1}]}')
grep -q "202" <<<"$headers" || fail "el pedido no fue aceptado: $headers"
TRACE=$(grep -i '^x-trace-id' <<<"$headers" | awk '{print $2}' | tr -d '\r')
[[ $TRACE =~ ^[0-9a-f]{32}$ ]] || fail "no llegó el header X-Trace-Id"
ok "pedido aceptado · X-Trace-Id: $TRACE"

echo "Esperando a que la telemetría llegue a Grafana LGTM (≈15 s)..."
trace=""
for _ in $(seq 1 20); do
  trace=$(lgtm "localhost:3200/api/traces/$TRACE" || true)
  grep -q '"stringValue":"inventory"' <<<"$trace" && grep -q 'orders.events process' <<<"$trace" && break
  sleep 3
done
grep -q '"stringValue":"orders"' <<<"$trace"    || fail "la traza no tiene spans de orders"
grep -q '"stringValue":"inventory"' <<<"$trace" || fail "la traza no tiene spans de inventory"
grep -q 'orders.events process' <<<"$trace"     || fail "falta el span del consumidor de Kafka"
ok "Tempo: una sola traza cruza orders (Quarkus) → Kafka → inventory (Python)"

logs=""
for _ in $(seq 1 10); do
  logs=$(lgtm -G localhost:3100/loki/api/v1/query_range \
    --data-urlencode "query={service_name=~\"orders|inventory\"} | trace_id=\"$TRACE\"" --data-urlencode "since=15m")
  grep -q "aceptado" <<<"$logs" && grep -q "RESERVED" <<<"$logs" && break
  sleep 3
done
grep -q "aceptado" <<<"$logs" && grep -q "RESERVED" <<<"$logs" || fail "Loki no tiene los logs de ambos servicios para la traza"
ok "Loki: los logs de los dos servicios se encuentran por el mismo traceId"

metric=""
for _ in $(seq 1 10); do
  metric=$(lgtm -G localhost:9090/api/v1/query --data-urlencode 'query=sum(inventory_reservations_total)')
  grep -q '"value"' <<<"$metric" && break
  sleep 3
done
grep -q '"value"' <<<"$metric" || fail "Prometheus no tiene la métrica inventory_reservations_total"
ok "Prometheus: métrica de negocio inventory_reservations_total"

echo "🎉 Lab 10 OK — abre http://localhost:3001 y busca la traza $TRACE"
