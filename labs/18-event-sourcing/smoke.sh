#!/usr/bin/env bash
# Prueba de humo del lab 18: saldo derivado de eventos, viaje en el tiempo, upcasting, concurrencia
# optimista contra lost update, snapshots, append-only y proyecciones reconstruibles.
set -euo pipefail
cd "$(dirname "$0")"

W=http://localhost:8106
fail() { echo "❌ $1"; exit 1; }
ok()   { echo "✅ $1"; }
wait_for() { for _ in $(seq 1 90); do curl -fsS -o /dev/null "$1" 2>/dev/null && return 0; sleep 2; done; fail "$1 no responde"; }
json() { python3 -c "import json,sys; d=json.load(sys.stdin, parse_float=str); print($1)"; }   # 360.00, no 360.0
sql() { docker compose exec -T postgres psql -U wallet -d wallet -tAc "$1" 2>&1; }
post() { curl -s -o /tmp/lab18-body -w '%{http_code}' -X POST "$W$1" -H 'Content-Type: application/json' -d "$2"; }
put() { curl -fsS -o /dev/null -X PUT "$W$1" -H 'Content-Type: application/json' -d "$2"; }
knobs() { put /admin/think-time "{\"ms\":$1}"; put /admin/retries "{\"maxAttempts\":$2}"; }
trap 'knobs 0 3 2>/dev/null || true' EXIT
open_wallet() { # dueño depósito → id
  local id; id=$(curl -fsS -X POST "$W/api/wallets" -H 'Content-Type: application/json' -d "{\"owner\":\"$1\"}" | json 'd["id"]')
  [ "$(post "/api/wallets/$id/deposits" "{\"amount\":$2,\"channel\":\"YAPE\"}")" = 200 ] || fail "depósito inicial"
  echo "$id"
}
two_at_once() { # ruta cuerpo → "código código"
  post "$1" "$2" > /tmp/lab18-c1 & post "$1" "$2" > /tmp/lab18-c2 & wait
  echo "$(cat /tmp/lab18-c1) $(cat /tmp/lab18-c2)" | tr ' ' '\n' | sort | tr '\n' ' ' | sed 's/ $//'
}
wait_projected() { for _ in $(seq 1 30); do [ "$(curl -fsS "$W/admin/projections" | json 'd["pending"]')" = 0 ] && return 0; sleep 0.5; done; fail "el proyector no se puso al día"; }

wait_for "$W/q/health/ready"
knobs 0 3

# 1. El saldo no está guardado en ninguna parte: se calcula.
[ "$(curl -fsS "$W/api/wallets/wallet-ana" | json 'd["balance"]')" = 360.00 ] || fail "saldo de ana"
[ "$(sql "select count(*) from information_schema.columns where table_name = 'events' and column_name like '%balance%'")" = 0 ] || fail "events no debería tener saldo"
ok "saldo de ana = 360.00, calculado con sus 7 eventos (no hay columna de saldo en el event store)"

# 2. Viaje en el tiempo.
past=$(curl -fsS "$W/api/wallets/wallet-ana?at=2026-11-01" | json 'd["balance"], d["version"]')
[ "$past" = "580.00 4" ] || fail "saldo al 1 de noviembre: $past"
ok "?at=2026-11-01 → 580.00 (los primeros 4 eventos): el saldo de una fecha pasada, sin tablas de auditoría"

# 3. Upcasting: el evento guardado sigue en v1; se lee en v2.
stored=$(sql "select payload from events where stream_id = 'wallet-ana' and version = 2")
read_as=$(curl -fsS "$W/api/wallets/wallet-ana/events" | json 'd[1]["storedSchemaVersion"], d[1]["schemaVersion"], d[1]["data"]["amount"], d[1]["data"]["channel"]')
[ "$stored" = '{"amountCents": 50000}' ] && [ "$read_as" = "1 2 500.00 DESCONOCIDO" ] || fail "upcasting: guardado $stored, leído $read_as"
ok "upcasting: en la tabla sigue $stored (v1); la app lo lee como v2 → amount 500.00, channel DESCONOCIDO"

# 4. Concurrencia optimista: dos retiros de 80 sobre 100, a la vez.
id=$(open_wallet eva 100.00)
knobs 300 3
codes=$(two_at_once "/api/wallets/$id/withdrawals" '{"amount":80.00}')
[ "$codes" = "200 422" ] || fail "dos retiros concurrentes: $codes"
attempts=$(curl -fsS "$W/api/wallets/$id/events" | json 'd[-1]["type"], d[-1]["metadata"]["attempt"]')
[ "$attempts" = "WithdrawalRejected 2" ] && [ "$(curl -fsS "$W/api/wallets/$id" | json 'd["balance"]')" = 20.00 ] || fail "concurrencia: $attempts"
ok "event sourcing: 2 retiros de 80 a la vez sobre 100 → 200 y 422; el segundo chocó con la versión, releyó y lo rechazó (intento 2). Saldo 20.00"

# 5. La versión ingenua pierde un retiro.
naive=$(curl -fsS -X POST "$W/api/naive/wallets" -H 'Content-Type: application/json' -d '{"owner":"eva","balance":100.00}' | json 'd["id"]')
codes=$(two_at_once "/api/naive/wallets/$naive/withdrawals" '{"amount":80.00}')
balance=$(curl -fsS "$W/api/naive/wallets/$naive" | json 'd["balance"]')
[ "$codes" = "200 200" ] && [ "$balance" = 20.00 ] || fail "ingenua: $codes, saldo $balance"
ok "ingenua (leer saldo → UPDATE): los 2 retiros responden 200, se entregaron 160 y el saldo dice $balance → lost update"

# 6. Sin reintentos, el que pierde la carrera recibe 409.
id2=$(open_wallet fede 100.00)
knobs 300 1
codes=$(two_at_once "/api/wallets/$id2/withdrawals" '{"amount":10.00}')
[ "$codes" = "200 409" ] || fail "sin reintentos: $codes"
ok "sin reintentos (maxAttempts=1) → 200 y 409 CONCURRENT_MODIFICATION: nada se pisa, el cliente decide"
knobs 0 3

# 7. Snapshots.
for _ in 1 2 3 4 5 6; do post "/api/wallets/$id/deposits" '{"amount":1.00}' >/dev/null; done
headers=$(curl -fsS -D - -o /dev/null "$W/api/wallets/$id" | tr -d '\r')
snap=$(echo "$headers" | awk -F': ' 'tolower($1) == "x-snapshot-version" {print $2}')
replayed=$(echo "$headers" | awk -F': ' 'tolower($1) == "x-events-replayed" {print $2}')
[ "$snap" -ge 5 ] && [ "$replayed" -lt 5 ] || fail "snapshot: v$snap + $replayed eventos"
ok "snapshot cada 5 eventos: el monedero (v10) se carga desde el snapshot v$snap + $replayed eventos, no desde los 10"

# 8. Append-only, también para quien entra por psql.
out=$(sql "update events set payload = '{\"amountCents\": 1}' where stream_id = 'wallet-ana' and version = 2" || true)
echo "$out" | grep -q "append-only" || fail "el event store aceptó un UPDATE: $out"
ok "UPDATE sobre events → rechazado por el trigger: 'El event store es append-only'"

# 9. Proyecciones: se ponen al día y se pueden rearmar desde cero.
wait_projected
before=$(curl -fsS "$W/api/balances" | json 'sorted((r["walletId"], r["balance"]) for r in d)')
echo "$before" | grep -q "'$id', '26.00'" || fail "balance_view no tiene el monedero de eva con 26.00: $before"
curl -fsS -o /dev/null -X POST "$W/admin/projections/rebuild"
wait_projected
after=$(curl -fsS "$W/api/balances" | json 'sorted((r["walletId"], r["balance"]) for r in d)')
[ "$before" = "$after" ] || fail "la proyección reconstruida es distinta"
ok "proyecciones: balance_view al día; TRUNCATE + releer los $(sql 'select count(*) from events') eventos → exactamente las mismas filas"

echo "🎉 Lab 18 OK"
