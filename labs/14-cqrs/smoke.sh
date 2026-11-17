#!/usr/bin/env bash
# Prueba de humo del lab 14: consistencia eventual, read-your-writes, la vista combinada de 3 servicios,
# filtros, proyector caído y puesta al día, idempotencia y reconstrucción de la vista.
# Tarda ~2,5 min.
set -euo pipefail
cd "$(dirname "$0")"

ORDERS=http://localhost:8102
HISTORY=http://localhost:8108
fail() { echo "❌ $1"; exit 1; }
ok()   { echo "✅ $1"; }
wait_for() { for _ in $(seq 1 90); do curl -fsS -o /dev/null "$1" 2>/dev/null && return 0; sleep 2; done; fail "$1 no responde"; }
json() { python3 -c "import json,sys; d=json.load(sys.stdin); print($1)"; }
place() { # cliente sku cantidad → id
  curl -fsS -X POST "$ORDERS/api/orders" -H 'Content-Type: application/json' \
    -d "{\"customerId\":\"$1\",\"items\":[{\"sku\":\"$2\",\"quantity\":$3}]}" | json 'd["id"]'
}
status_of() { curl -s "$HISTORY/api/history/$1" | json 'd.get("status", d.get("code"))'; }
view() { curl -fsS "$HISTORY/api/history/status" | json "d['$1']"; }
lag() { # eventos que el proyector todavía no aplicó (suma del LAG de su consumer group)
  docker compose exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
    --describe --group order-history 2>/dev/null | awk '$6 ~ /^[0-9]+$/ {s += $6} END {print s + 0}'
}
wait_caught_up() { # segundos
  sleep 7   # deja que fulfillment publique los pagos y envíos pendientes (2 s y 5 s)
  for _ in $(seq 1 "$1"); do [ "$(lag)" = 0 ] && return 0; sleep 1; done
  fail "el proyector no se puso al día (lag $(lag))"
}
projector() { # VAR=valor... → recrea el proyector con esas variables
  env "$@" docker compose up -d --no-build history-projector >/dev/null 2>&1
}

wait_for "$ORDERS/q/health/ready"; wait_for "$HISTORY/health"
projector PROJECTION_VERSION=1 PROJECTION_DELAY_MS=1500

# 1. Consistencia eventual: el comando responde YA; la vista se entera después.
id=$(place ana INV-001 2)
first=$(status_of "$id")
[ "$first" = NOT_YET_PROJECTED ] || fail "recién creado, el pedido no debería estar en la vista todavía: $first"
ok "POST /api/orders → 201 al instante; GET inmediato al historial → 404 NOT_YET_PROJECTED (la vista va atrasada)"

start=$(date +%s%N)
code=$(curl -s -o /tmp/lab14-ryw -w '%{http_code}' "$HISTORY/api/history/$id?minVersion=1&waitMs=8000")
ms=$(( ($(date +%s%N) - start) / 1000000 ))
[ "$code" = 200 ] && [ "$(json 'd["status"]' < /tmp/lab14-ryw)" = RECIBIDO ] || fail "read-your-writes: $code $(cat /tmp/lab14-ryw)"
ok "read-your-writes (?minVersion=1&waitMs=8000) → 1 petición, el servidor esperó ${ms} ms hasta que la vista lo tuvo"

# 2. Un documento, tres servicios: pedidos + pagos + envíos, sin JOIN al leer.
seen="RECIBIDO"
for _ in $(seq 1 60); do
  s=$(status_of "$id"); [ "${seen##* }" != "$s" ] && seen="$seen $s"
  [ "$s" = ENVIADO ] && break; sleep 0.3
done
[ "$seen" = "RECIBIDO PAGADO ENVIADO" ] || fail "el pedido debería pasar por RECIBIDO → PAGADO → ENVIADO: $seen"
doc=$(curl -fsS "$HISTORY/api/history/$id")
[ "$(echo "$doc" | json 'd["summary"]')" = "2 × Paraguas solar" ] && echo "$doc" | json 'd["shipment"]["trackingCode"]' | grep -q '^RS-' \
  || fail "documento incompleto: $doc"
ok "el documento junta 3 servicios: ${seen// / → }, '2 × Paraguas solar', seguimiento $(echo "$doc" | json 'd["shipment"]["trackingCode"]')"

# 3. Consultas que en el lado de comandos necesitarían JOIN entre servicios.
taza=$(place beto INV-004 1)
curl -fsS -o /dev/null "$HISTORY/api/history/$taza?minVersion=1&waitMs=8000"
[ "$(curl -fsS "$HISTORY/api/history?customerId=beto&q=TAZA" | json 'sum(o["id"] == "'"$taza"'" for o in d)')" = 1 ] || fail "búsqueda por texto"
[ "$(curl -fsS "$HISTORY/api/history?customerId=ana&q=taza" | json 'len(d)')" = 0 ] || fail "ana no debería ver pedidos de beto"
[ "$(curl -fsS "$HISTORY/api/history?customerId=ana&status=ENVIADO" | json 'all(o["status"] == "ENVIADO" for o in d) and len(d) > 0')" = True ] \
  || fail "filtro por estado"
ok "filtros: ?q=TAZA encuentra el pedido de beto (sin distinguir mayúsculas), ana no lo ve, ?status=ENVIADO solo trae enviados"

# 4. Proyector caído: la lectura SIGUE funcionando (con datos que se van quedando viejos).
wait_caught_up 30
before=$(view documents)
docker compose stop history-projector >/dev/null 2>&1
late1=$(place beto INV-002 1); late2=$(place beto INV-003 1); late3=$(place beto INV-001 3)
sleep 2
[ "$(view documents)" = "$before" ] && [ "$(status_of "$late1")" = NOT_YET_PROJECTED ] || fail "con el proyector detenido la vista no debería cambiar"
ok "proyector detenido → la API de lectura responde igual ($before pedidos), pero no ve los 3 pedidos nuevos"
docker compose start history-projector >/dev/null 2>&1
wait_caught_up 60
for o in $late1 $late2 $late3; do [ "$(status_of "$o")" = ENVIADO ] || fail "el pedido $o no se puso al día: $(status_of "$o")"; done
ok "proyector de vuelta → retoma desde su último offset: los 3 pedidos y sus pagos y envíos aparecen, ENVIADO"

# 5. Idempotencia: Kafka puede reentregar. Rebobinamos el consumer group a 0 y se reaplica TODO.
docs=$(view documents); applied=$(view eventsApplied)
docker compose stop history-projector >/dev/null 2>&1
out=$(docker compose exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group order-history \
  --reset-offsets --to-earliest --all-topics --execute 2>&1)
echo "$out" | grep -q Error && fail "no se pudo rebobinar el consumer group: $out"
projector PROJECTION_VERSION=1 PROJECTION_DELAY_MS=0
wait_caught_up 60
[ "$(view documents)" = "$docs" ] && [ "$(view eventsApplied)" = "$applied" ] || fail "reaplicar eventos cambió la vista: $(view documents) docs, $(view eventsApplied) eventos"
replayed=$(docker compose logs --since 30s history-projector | grep -c 'ya aplicado' || true)
ok "idempotencia: $replayed eventos reentregados, 0 aplicados dos veces ($docs pedidos, $applied eventos, igual que antes)"

# 6. Reconstruir la vista: otra versión de la proyección = vaciar y volver a leer los topics desde el principio.
projector PROJECTION_VERSION=2 PROJECTION_DELAY_MS=0
for _ in $(seq 1 30); do [ "$(view projectionVersion)" = 2 ] && break; sleep 1; done
wait_caught_up 60
[ "$(view documents)" = "$docs" ] && [ "$(status_of "$id")" = ENVIADO ] || fail "la vista reconstruida no coincide: $(view documents) docs"
ok "reconstrucción (PROJECTION_VERSION=2) → vista vacía y rearmada desde Kafka: los mismos $docs pedidos"

# Deja todo como estaba: v1 (otra reconstrucción, rápida) y con la demora normal.
projector PROJECTION_VERSION=1 PROJECTION_DELAY_MS=0
for _ in $(seq 1 30); do [ "$(view projectionVersion)" = 1 ] && break; sleep 1; done
wait_caught_up 60
projector PROJECTION_VERSION=1 PROJECTION_DELAY_MS=1500

echo "🎉 Lab 14 OK"
