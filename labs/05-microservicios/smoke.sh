#!/usr/bin/env bash
# Prueba de humo del lab 05.
#
#   ./smoke.sh          # arquitectura correcta (puertos 81xx)
#   ./smoke.sh naive    # versión ingenua       (puertos 91xx)
#   ./smoke.sh prod     # ambiente prod (solo orders y report expuestos)
set -euo pipefail

case "${1:-correct}" in
  correct) ORDERS=8102; INVENTORY=8103; REPORT=8109; SOURCE=api ;;
  naive)   ORDERS=9102; INVENTORY=9103; REPORT=9109; SOURCE=database ;;
  prod)    ORDERS=8102; INVENTORY="";   REPORT=8109; SOURCE=api ;;
  *) echo "Uso: $0 [correct|naive|prod]"; exit 2 ;;
esac

fail() { echo "❌ $1"; exit 1; }
ok()   { echo "✅ $1"; }

for _ in $(seq 1 60); do
  curl -fsS -o /dev/null "http://localhost:$REPORT/health" 2>/dev/null && break
  sleep 2
done

order() {
  curl -fsS -X POST "http://localhost:$ORDERS/api/orders" -H 'Content-Type: application/json' -d "$1"
}
expect() {
  grep -qF -- "$2" <<<"$1" || fail "$3: se esperaba '$2', llegó: $1"
  ok "$3"
}

stock() { [ -n "$INVENTORY" ] && curl -fsS "http://localhost:$INVENTORY/api/stock/INV-001" || echo "(inventario no expuesto)"; }
before=$(stock)
expect "$(order '{"customerId":"ana","currency":"PEN","items":[{"sku":"INV-001","quantity":1,"unitPrice":49.90}]}')" \
  '"status":"CONFIRMED"' "pedido con stock y pago aprobado → CONFIRMED"
if [ -n "$INVENTORY" ]; then
  after=$(stock)
  [ "$before" != "$after" ] || fail "el stock de INV-001 debía bajar ($before → $after)"
  ok "inventario descontó el stock ($before → $after)"
fi

expect "$(order '{"customerId":"ana","currency":"PEN","items":[{"sku":"INV-002","quantity":1,"unitPrice":35.00}]}')" \
  '"rejectionReason":"Sin stock' "pedido sin stock → REJECTED"

expect "$(order '{"customerId":"ana","currency":"PEN","items":[{"sku":"INV-004","quantity":10,"unitPrice":72.50}]}')" \
  '"rejectionReason":"Pago rechazado' "pedido de 725.00 (límite 500) → REJECTED por pagos"

expect "$(curl -fsS "http://localhost:$REPORT/api/report")" "\"source\":\"$SOURCE\"" "el reporte responde (fuente: $SOURCE)"

echo "🎉 Lab 05 ($SOURCE) OK"
