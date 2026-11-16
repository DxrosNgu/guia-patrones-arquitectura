#!/usr/bin/env bash
# Prueba de humo del lab 12: Idempotency Key en la API e Idempotent Consumer en Kafka.
set -euo pipefail
cd "$(dirname "$0")"

PAYMENTS=http://localhost:8104
INVENTORY=http://localhost:8103
fail() { echo "❌ $1"; exit 1; }
ok()   { echo "✅ $1"; }

wait_for() { for _ in $(seq 1 90); do curl -fsS -o /dev/null "$1" 2>/dev/null && return 0; sleep 2; done; fail "$1 no responde"; }
pay() { # key orderId amount
  local header=(); [ -n "$1" ] && header=(-H "Idempotency-Key: $1")
  curl -sS -D - -X POST "$PAYMENTS/api/payments" -H 'Content-Type: application/json' "${header[@]}" \
    -d "{\"orderId\":\"$2\",\"customerId\":\"ana\",\"amount\":$3,\"currency\":\"PEN\"}"
}
payments_of() { curl -fsS "$PAYMENTS/api/payments?orderId=$1" | grep -o '"id"' | wc -l | tr -d ' '; }
stock() { curl -fsS "$INVENTORY/api/stock" | grep -o "\"sku\":\"$1\",\"available\":[0-9]*" | grep -o '[0-9]*$'; }
wait_processed() { for _ in $(seq 1 30); do curl -fsS "$INVENTORY/api/reservations" | grep -q "\"orderId\":\"$1\"" && return 0; sleep 1; done; fail "no se procesó $1"; }

wait_for "$PAYMENTS/q/health/ready"
wait_for "$INVENTORY/health"
RUN=$(date +%s)

# ── API: Idempotency-Key ─────────────────────────────────────────────────
ORDER="pedido-$RUN-a"; KEY="clave-$RUN-a"
for _ in 1 2 3 4 5; do pay "$KEY" "$ORDER" 120 > "/tmp/lab12-$RUN-$_" & done; wait
[ "$(payments_of "$ORDER")" = 1 ] || fail "5 reintentos simultáneos con la misma clave cobraron $(payments_of "$ORDER") veces"
ok "5 reintentos simultáneos con la misma Idempotency-Key → 1 solo cobro"

ORDER="pedido-$RUN-b"
pay "" "$ORDER" 120 > /dev/null; pay "" "$ORDER" 120 > /dev/null
[ "$(payments_of "$ORDER")" = 2 ] || fail "sin clave se esperaban 2 cobros"
ok "sin Idempotency-Key, 2 reintentos → 2 cobros (versión ingenua)"

KEY="clave-$RUN-c"
pay "$KEY" "pedido-$RUN-c" 50 > /dev/null
pay "$KEY" "pedido-$RUN-c" 70 | grep -q "422" || fail "misma clave con otro cuerpo debía dar 422"
ok "misma clave con otro cuerpo → 422"

# ── Kafka: Idempotent Consumer ──────────────────────────────────────────
before=$(stock INV-004)
./publish-event.sh "evt-$RUN-1" "pedido-$RUN-1" INV-004 1 > /dev/null
./publish-event.sh "evt-$RUN-1" "pedido-$RUN-1" INV-004 1 > /dev/null   # el MISMO evento otra vez
wait_processed "pedido-$RUN-1"; sleep 3
[ "$(stock INV-004)" = $((before - 1)) ] || fail "el evento duplicado descontó stock dos veces ($before → $(stock INV-004))"
ok "evento entregado dos veces → stock descontado UNA vez ($before → $(stock INV-004))"

# Caos: el consumidor muere entre procesar y confirmar el offset.
CHAOS_CRASH_BEFORE_COMMIT=true docker compose up -d --wait inventory > /dev/null 2>&1
before=$(stock INV-004)
./publish-event.sh "evt-$RUN-2" "pedido-$RUN-2" INV-004 1 > /dev/null
for _ in $(seq 1 60); do
  docker compose logs inventory 2>/dev/null | grep -q "evt-$RUN-2 (pedido pedido-$RUN-2) ya procesado" && break; sleep 2
done
docker compose logs inventory 2>/dev/null | grep -q "evt-$RUN-2 (pedido pedido-$RUN-2) ya procesado" \
  || fail "tras la caída, Kafka debía reentregar el evento y el consumidor ignorarlo"
[ "$(stock INV-004)" = $((before - 1)) ] || fail "tras la caída el stock se descontó dos veces"
ok "caída antes del commit → Kafka reentrega → duplicado ignorado (stock $before → $(stock INV-004))"
docker compose up -d --wait inventory > /dev/null 2>&1

echo "🎉 Lab 12 OK"
