#!/usr/bin/env bash
# Prueba de humo del lab 11: timeout, retry, circuit breaker, bulkhead, fallback y tenacity.
set -euo pipefail
cd "$(dirname "$0")"

PAY=http://localhost:8104
PROVIDER=http://localhost:8122
CHECKOUT=http://localhost:8110
fail() { echo "❌ $1"; exit 1; }
ok()   { echo "✅ $1"; }
wait_for() { for _ in $(seq 1 90); do curl -fsS -o /dev/null "$1" 2>/dev/null && return 0; sleep 2; done; fail "$1 no responde"; }
chaos() { curl -fsS -o /dev/null -X PUT "$PROVIDER/admin/chaos" -H 'Content-Type: application/json' -d "$1"; }
requests() { curl -fsS "$PROVIDER/admin/stats" | python3 -c "import json,sys; print(json.load(sys.stdin)['requests'])"; }
circuit() { curl -fsS "$PAY/api/resilience" | python3 -c "import json,sys; print(json.load(sys.stdin)['circuit'])"; }
pay() { # camino orderId → "código ms estado"
  curl -s -o /tmp/lab11-body -w "%{http_code} %{time_total}" -X POST "$PAY/api/payments$1" -H 'Content-Type: application/json' \
       -d "{\"orderId\":\"$2\",\"amount\":${3:-25}}" | awk '{printf "%s %d", $1, $2*1000}'
  echo " $(python3 -c "import json; print(json.load(open('/tmp/lab11-body')).get('status',''))" 2>/dev/null)"
}
cleanup() { chaos '{}' || true; docker compose start payments >/dev/null 2>&1 || true; }
trap cleanup EXIT

wait_for "$PAY/q/health/ready"; wait_for "$PROVIDER/health"; wait_for "$CHECKOUT/health"
curl -fsS -o /dev/null -X POST "$PROVIDER/admin/reset"
RUN=$RANDOM

read -r code ms status <<<"$(pay "" ok-$RUN)"
[ "$code" = 201 ] && [ "$status" = APPROVED ] || fail "con la pasarela sana el pago debería aprobarse ($code $status)"
ok "pasarela sana → 201 APPROVED en ${ms} ms"

read -r code ms status <<<"$(pay "" big-$RUN 5000)"
[ "$code" = 402 ] || fail "un monto grande debería rechazarse con 402 ($code)"
ok "monto rechazado por la pasarela → 402 DECLINED, sin reintentos ni fallback (es negocio, no una falla)"

chaos '{"latencyMs":3000}'
read -r code ms status <<<"$(pay /naive slow-naive-$RUN)"
[ "$ms" -ge 2900 ] || fail "sin timeout debería esperar los 3 s de la pasarela (tardó $ms ms)"
ok "pasarela lenta (3 s) · ingenuo → espera ${ms} ms con un hilo bloqueado"
read -r code ms status <<<"$(pay "" slow-$RUN)"
[ "$code" = 202 ] && [ "$status" = PENDING_CONFIRMATION ] && [ "$ms" -lt 2500 ] || fail "con timeout: $code $status en $ms ms"
ok "pasarela lenta (3 s) · resiliente → timeout 500 ms × 3 intentos → fallback PENDING_CONFIRMATION en ${ms} ms"
chaos '{}'
sleep 6   # que el circuito no arrastre las fallas de la prueba anterior

chaos '{"down":true}'
before=$(requests)
read -r code ms status <<<"$(pay /naive-retry storm-$RUN)"
storm=$(( $(requests) - before ))
[ "$code" = 502 ] && [ "$storm" = 6 ] || fail "reintentos inmediatos: $code, $storm llamadas"
ok "pasarela caída · reintentos inmediatos → 502 y $storm llamadas a la pasarela por UN pago (retry storm)"

for i in 1 2; do pay "" down-$i-$RUN >/dev/null; done
[ "$(circuit)" = OPEN ] || fail "tras 6 fallas el circuito debería estar abierto (está $(circuit))"
before=$(requests)
read -r code ms status <<<"$(pay "" open-$RUN)"
[ "$status" = PENDING_CONFIRMATION ] && [ "$ms" -lt 300 ] && [ "$(requests)" = "$before" ] \
  || fail "con el circuito abierto: $status en $ms ms, llamadas $before → $(requests)"
ok "pasarela caída · resiliente → el circuito se ABRE: el siguiente pago responde en ${ms} ms sin llamar a la pasarela"

chaos '{}'
for _ in $(seq 1 30); do
  pending=$(curl -fsS "$PAY/api/resilience" | python3 -c "import json,sys; print(json.load(sys.stdin)['pendingConfirmation'])")
  [ "$pending" = 0 ] && [ "$(circuit)" = CLOSED ] && break
  sleep 1
done
[ "$pending" = 0 ] && [ "$(circuit)" = CLOSED ] || fail "al volver la pasarela, el circuito debería cerrarse y los pendientes confirmarse (pendientes=$pending, circuito=$(circuit))"
[ "$(curl -fsS "$PAY/api/payments/open-$RUN" | python3 -c "import json,sys; print(json.load(sys.stdin)['status'])")" = APPROVED ] \
  || fail "el pago pendiente open-$RUN debería estar APPROVED"
ok "pasarela de vuelta → semiabierto → CERRADO, y el reconciliador confirmó los pagos pendientes"

chaos '{"latencyMs":400}'
for i in $(seq 1 10); do pay "" bulk-$i-$RUN & done > /tmp/lab11-bulk; wait
approved=$(grep -c APPROVED /tmp/lab11-bulk || true); pending=$(grep -c PENDING /tmp/lab11-bulk || true)
[ "$approved" -le 5 ] && [ "$pending" -ge 1 ] || fail "bulkhead: $approved aprobados, $pending pendientes"
ok "10 pagos a la vez con la pasarela a 400 ms → bulkhead de 5: $approved aprobados, $pending al fallback al instante"
chaos '{}'

docker compose stop payments >/dev/null 2>&1
start=$(date +%s%N)
code=$(curl -s -o /tmp/lab11-co -w "%{http_code}" -X POST "$CHECKOUT/api/checkout" -H 'Content-Type: application/json' -d '{"orderId":"co-1","amount":10}')
took=$(( ($(date +%s%N) - start) / 1000000 ))
[ "$code" = 503 ] && grep -q '"attempts":3' /tmp/lab11-co && [ "$took" -ge 500 ] || fail "checkout con pagos caído: $code en $took ms $(cat /tmp/lab11-co)"
ok "pagos caído · checkout (tenacity) → 3 intentos con esperas crecientes y jitter → 503 en ${took} ms"
docker compose start payments >/dev/null 2>&1
wait_for "$PAY/q/health/ready"

metric=""
for _ in $(seq 1 15); do
  metric=$(docker compose exec -T lgtm curl -s -G localhost:9090/api/v1/query --data-urlencode 'query=payments_circuit_state' || true)
  grep -q '"value"' <<<"$metric" && break
  sleep 2
done
grep -q '"value"' <<<"$metric" || fail "Prometheus no tiene payments_circuit_state"
ok "métricas en Grafana: payments_circuit_state, payments_outcomes_total, payments_provider_calls_total"

echo "🎉 Lab 11 OK — Grafana: http://localhost:3001 (admin/admin)"
