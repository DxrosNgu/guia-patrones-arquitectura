#!/usr/bin/env bash
# Prueba de humo del lab 20: las 5 etapas del Strangler Fig, el canary por pesos, el rollback en caliente
# y la sincronización de pedidos entre el legado y el servicio nuevo. Tarda ~1 min.
set -euo pipefail
cd "$(dirname "$0")"

GW=http://localhost:8080
LEGACY=http://localhost:8120
ORDERS=http://localhost:8102
fail() { echo "❌ $1"; exit 1; }
ok()   { echo "✅ $1"; }
wait_for() { for _ in $(seq 1 90); do curl -fsS -o /dev/null "$1" 2>/dev/null && return 0; sleep 2; done; fail "$1 no responde"; }
json() { python3 -c "import json,sys; d=json.load(sys.stdin); print($1)"; }
stage() { curl -fsS -o /dev/null -X PUT "$GW/admin/migration/stage/$1"; }
weights() { curl -fsS -o /dev/null -X PUT "$GW/admin/migration" -H 'Content-Type: application/json' -d "$1"; }
sync() { curl -fsS -o /dev/null -X PUT "$ORDERS/admin/sync" -H 'Content-Type: application/json' -d "{\"enabled\":$1}"; }
served_by() { curl -fsS -o /dev/null -D - "$GW$1" | tr -d '\r' | awk -F': ' 'tolower($1) == "x-served-by" {print $2}'; }
count_new() { # ruta veces → cuántas atendió el servicio nuevo de pedidos
  local n=0; for _ in $(seq 1 "$2"); do [ "$(served_by "$1")" = orders ] && n=$((n + 1)); done; echo $n
}
place() { curl -fsS -X POST "$GW/api/orders" -H 'Content-Type: application/json' -d "{\"customer\":\"$1\",\"sku\":\"INV-001\",\"quantity\":1}" | json 'd["id"]'; }
restore() {
  docker compose start legacy-shop >/dev/null 2>&1 || true
  wait_for "$LEGACY/health"; sync true; stage 1
}
trap restore EXIT

wait_for "$GW/actuator/health"; wait_for "$ORDERS/q/health/ready"; wait_for "$LEGACY/health"
sync true; stage 1
who="smoke$RANDOM"

# Etapa 1: proxy transparente.
[ "$(served_by /api/catalog/products)" = legacy-shop ] && [ "$(served_by "/api/orders?customer=ana")" = legacy-shop ] || fail "etapa 1"
old=$(place "$who")
[ "$old" -lt 500001 ] || fail "en la etapa 1 el pedido debería crearlo el legado: $old"
ok "etapa 1 → todo pasa por el gateway al legado; el pedido $old lo creó el legado"

# Etapa 2: el catálogo pasa al servicio nuevo, con el MISMO contrato.
stage 2
[ "$(served_by /api/catalog/products/INV-004)" = catalog ] || fail "etapa 2: catálogo"
same=$(python3 -c "
import json, urllib.request
get = lambda u: json.load(urllib.request.urlopen(u))
print(get('$GW/api/catalog/products') == get('$LEGACY/api/catalog/products'))")
[ "$same" = True ] && [ "$(served_by "/api/orders?customer=ana")" = legacy-shop ] || fail "etapa 2: el catálogo nuevo no responde igual que el legado"
ok "etapa 2 → /api/catalog lo atiende el servicio nuevo, con la misma respuesta que el legado; /api/orders sigue en el legado"

# Etapa 3: canary, 10 % de /api/orders al servicio nuevo.
stage 3
sleep 2   # el pedido de la etapa 1 llega al servicio nuevo por la sincronización
n=$(count_new "/api/orders/$old" 200)
[ "$n" -ge 5 ] && [ "$n" -le 45 ] || fail "canary 10 %: $n de 200 al servicio nuevo"
ok "etapa 3 → canary: $n de 200 peticiones a /api/orders fueron al servicio nuevo (~10 %), y todas encontraron el pedido $old"

ids=(); for _ in $(seq 1 30); do ids+=("$(place "$who")"); done
created_new=$(printf '%s\n' "${ids[@]}" | awk '$1 >= 500001' | wc -l)
[ "$created_new" -ge 1 ] || fail "ningún pedido del canary lo creó el servicio nuevo"
sleep 3
for id in "${ids[@]}"; do
  curl -fsS -o /dev/null "$LEGACY/api/orders/$id" || fail "el pedido $id no está en el legado"
  curl -fsS -o /dev/null "$ORDERS/api/orders/$id" || fail "el pedido $id no está en el servicio nuevo"
done
ok "sincronización: de 30 pedidos, $created_new los creó el servicio nuevo y $((30 - created_new)) el legado; a los 3 s los 30 están en las DOS bases"

# Rollback: todo /api/orders vuelve al legado, al instante y sin perder pedidos.
weights '{"ordersToNewPercent": 0}'
[ "$(count_new "/api/orders?customer=$who" 30)" = 0 ] || fail "rollback: todavía llegan pedidos al servicio nuevo"
legacy_count=$(curl -fsS "$GW/api/orders?customer=$who" | json 'len(d)')
[ "$legacy_count" = 31 ] || fail "rollback: el legado ve $legacy_count pedidos de $who, deberían ser 31"
ok "rollback (ordersToNewPercent=0) → 0 de 30 al servicio nuevo, y el legado muestra los 31 pedidos, también los $created_new creados en el nuevo"

# Etapa 4: todo /api/orders al servicio nuevo.
stage 4
[ "$(count_new "/api/orders?customer=$who" 20)" = 20 ] || fail "etapa 4"
[ "$(curl -fsS "$GW/api/orders?customer=$who" | json 'len(d)')" = 31 ] || fail "etapa 4: faltan pedidos en el servicio nuevo"
ok "etapa 4 → 20 de 20 al servicio nuevo, con la historia completa del cliente (31 pedidos, incluidos los del legado)"

# Etapa 5: se apaga el legado.
stage 5; sync false
docker compose stop legacy-shop >/dev/null 2>&1
[ "$(served_by /api/catalog/products)" = catalog ] && [ "$(served_by "/api/orders/$old")" = orders ] || fail "etapa 5"
late=$(place "$who")
code=$(curl -s -o /dev/null -w '%{http_code}' "$GW/api/legacy-only")
[ "$late" -ge 500001 ] && [ "$code" = 404 ] || fail "etapa 5: pedido $late, ruta no migrada $code"
ok "etapa 5 → legacy-shop detenido: catálogo y pedidos siguen funcionando (pedido $late); lo que nunca se migró da 404"

# Volver atrás: el legado se pone al día con lo que se creó mientras estaba apagado.
restore
sleep 3
curl -fsS -o /dev/null "$LEGACY/api/orders/$late" || fail "el legado no recibió el pedido $late al volver"
ok "el legado vuelve y la sincronización le manda el pedido $late, creado mientras estaba apagado"

echo "🎉 Lab 20 OK"
