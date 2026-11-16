#!/usr/bin/env bash
# Prueba de humo del lab 03: cada invariante, contra el modelo anémico y contra el agregado.
set -euo pipefail
cd "$(dirname "$0")"

BASE=http://localhost:8102
fail() { echo "❌ $1"; exit 1; }
ok()   { echo "✅ $1"; }

for _ in $(seq 1 90); do curl -fsS -o /dev/null "$BASE/q/health/ready" 2>/dev/null && break; sleep 2; done

call() { # método ruta [json] → código (cuerpo en /tmp/lab03-body)
  curl -s -o /tmp/lab03-body -w "%{http_code}" -X "$1" "$BASE$2" -H 'Content-Type: application/json' ${3:+-d "$3"}
}
field() { grep -o "\"$1\":[^,}]*" /tmp/lab03-body | head -1 | cut -d: -f2- | tr -d '"'; }
expect() { # código esperado, código real, código de error esperado (opcional), mensaje
  [ "$2" = "$1" ] || fail "$4 (HTTP $2, se esperaba $1): $(cat /tmp/lab03-body)"
  [ -z "$3" ] || grep -q "\"$3\"" /tmp/lab03-body || fail "$4: falta $3 en $(cat /tmp/lab03-body)"
}

echo "── Modelo anémico ──"
call POST /api/anemic/orders '{"customerId":"ana","lines":[{"sku":"INV-001","unitPrice":0.1,"quantity":1},{"sku":"INV-002","unitPrice":0.2,"quantity":1}]}' >/dev/null
ANEMIC=$(field id)
[ "$(field total)" = "0.30000000000000004" ] || fail "se esperaba el error de double en el total"
ok "anémico: total con double = $(field total)"
call POST "/api/anemic/orders/$ANEMIC/lines" '{"sku":"INV-003","unitPrice":5,"quantity":50}' >/dev/null
[ "$(field total)" = "0.30000000000000004" ] || fail "el anémico debería olvidar recalcular el total"
ok "anémico: acepta 50 unidades y el total NO se recalcula ($(field total))"
call PUT "/api/anemic/orders/$ANEMIC/status" '{"status":"banana"}' >/dev/null
[ "$(field status)" = "banana" ] || fail "el anémico debería aceptar cualquier estado"
ok "anémico: estado = banana"

echo "── Agregado Order ──"
expect 201 "$(call POST /api/orders '{"customerId":"ana","currency":"PEN"}')" "" "crear pedido"
ID=$(field id)
call POST "/api/orders/$ID/lines" '{"sku":"INV-001","unitPrice":0.10,"quantity":1}' >/dev/null
expect 200 "$(call POST "/api/orders/$ID/lines" '{"sku":"INV-002","unitPrice":0.20,"quantity":1}')" "" "agregar línea"
[ "$(field total)" = "0.30" ] || fail "el total debería ser 0.30, es $(field total)"
ok "DDD: total con Money = $(field total)"
expect 422 "$(call POST "/api/orders/$ID/lines" '{"sku":"INV-003","unitPrice":5,"quantity":50}')" MAX_QUANTITY_EXCEEDED "50 unidades"
ok "DDD: 50 unidades → 422 MAX_QUANTITY_EXCEEDED"
expect 422 "$(call POST "/api/orders/$ID/lines" '{"sku":"INV-003","unitPrice":5,"currency":"USD","quantity":1}')" CURRENCY_MISMATCH "otra moneda"
ok "DDD: dólares en un pedido en soles → 422 CURRENCY_MISMATCH"
expect 200 "$(call POST "/api/orders/$ID/lines" '{"sku":"INV-004","unitPrice":10,"quantity":1,"total":1}')" "" "total enviado por el cliente"
[ "$(field total)" = "10.30" ] || fail "el total enviado por el cliente no debería contar ($(field total))"
ok "DDD: el \"total\" que manda el cliente se ignora (10.30)"
expect 200 "$(call POST "/api/orders/$ID/confirm")" "" "confirmar"
ok "DDD: confirmado"
expect 409 "$(call POST "/api/orders/$ID/lines" '{"sku":"INV-005","unitPrice":1,"quantity":1}')" ORDER_NOT_EDITABLE "editar confirmado"
ok "DDD: editar un pedido confirmado → 409 ORDER_NOT_EDITABLE"
expect 409 "$(call POST "/api/orders/$ID/ship")" INVALID_TRANSITION "enviar sin pagar"
ok "DDD: enviar sin pagar → 409 INVALID_TRANSITION"
curl -fsS "$BASE/api/events" | grep -q "$ID" || fail "no se publicó OrderConfirmed"
docker compose logs orders 2>/dev/null | grep -q "📧 Pedido $ID confirmado" || fail "el listener no reaccionó"
ok "DDD: evento OrderConfirmed publicado y procesado por el listener"

echo "── Concurrencia ──"
call POST /api/orders '{"customerId":"beto","currency":"PEN"}' >/dev/null
RACE=$(field id)
for i in 1 2 3 4 5 6; do
  curl -s -o /dev/null -w "%{http_code}\n" -X POST "$BASE/api/orders/$RACE/lines" -H 'Content-Type: application/json' \
       -d "{\"sku\":\"INV-10$i\",\"unitPrice\":1,\"quantity\":1}" &
done > /tmp/lab03-race; wait
ACCEPTED=$(grep -c '^200$' /tmp/lab03-race || true)
REJECTED=$(grep -c '^409$' /tmp/lab03-race || true)
LINES=$(curl -fsS "$BASE/api/orders/$RACE" | grep -o '"sku"' | wc -l)
[ "$LINES" -eq "$ACCEPTED" ] || fail "líneas guardadas ($LINES) ≠ respuestas 200 ($ACCEPTED)"
ok "DDD: 6 cambios simultáneos → $ACCEPTED aceptados, $REJECTED con 409 CONCURRENT_MODIFICATION, $LINES líneas guardadas (ninguno se perdió en silencio)"

echo "🎉 Lab 03 OK"
