#!/usr/bin/env bash
# Prueba de humo del lab 13: hit/miss, invalidación, caché local con 2 instancias y stampede.
set -euo pipefail
cd "$(dirname "$0")"

C1=http://localhost:8101
C2=http://localhost:8111
fail() { echo "❌ $1"; exit 1; }
ok()   { echo "✅ $1"; }
wait_for() { for _ in $(seq 1 90); do curl -fsS -o /dev/null "$1" 2>/dev/null && return 0; sleep 2; done; fail "$1 no responde"; }
read_() { # base sku modo → "X-Cache ms precio"
  curl -s -D /tmp/lab13-h -o /tmp/lab13-b -w "%{time_total}" "$1/api/inventions/$2?cache=$3" | awk '{printf "%d", $1*1000}' > /tmp/lab13-t
  echo "$(grep -i '^x-cache' /tmp/lab13-h | awk '{print $2}' | tr -d '\r') $(cat /tmp/lab13-t) $(python3 -c "import json; print(json.load(open('/tmp/lab13-b'))['price'])")"
}
price() { curl -fsS -o /dev/null -X PUT "$1/api/inventions/$2/price" -H 'Content-Type: application/json' -d "{\"price\":$3}"; }
evict() { curl -fsS -o /dev/null -X DELETE "$C1/admin/cache/$1"; }
protection() { for c in $C1 $C2; do curl -fsS -o /dev/null -X PUT "$c/admin/stampede-protection" -H 'Content-Type: application/json' -d "{\"enabled\":$1}"; done; }
reset_stats() { for c in $C1 $C2; do curl -fsS -o /dev/null -X PUT "$c/admin/stats/reset"; done; }
db_queries() { echo $(( $(curl -fsS "$C1/api/stats" | python3 -c "import json,sys; print(json.load(sys.stdin)['dbQueries'])") + $(curl -fsS "$C2/api/stats" | python3 -c "import json,sys; print(json.load(sys.stdin)['dbQueries'])") )); }

wait_for "$C1/q/health/ready"; wait_for "$C2/q/health/ready"
for s in INV-001 INV-002 INV-003; do evict $s; done
curl -fsS -o /dev/null -X DELETE "$C2/admin/cache/INV-002"

read -r cache ms p <<<"$(read_ $C1 INV-003 none)"
[ "$cache" = BYPASS ] && [ "$ms" -ge 190 ] || fail "sin caché debería ir a la base (~200 ms): $cache $ms ms"
ok "sin caché → la consulta cara cada vez: ${ms} ms"
read -r cache ms1 p <<<"$(read_ $C1 INV-003 redis)"; read -r cache2 ms2 p <<<"$(read_ $C1 INV-003 redis)"
[ "$cache" = MISS ] && [ "$cache2" = HIT ] && [ "$ms2" -lt 50 ] || fail "Redis: $cache $ms1 ms → $cache2 $ms2 ms"
ok "Redis → MISS ${ms1} ms (base + guardar), luego HIT ${ms2} ms"

price $C1 INV-003 120.00
read -r cache ms p <<<"$(read_ $C2 INV-003 redis)"
[ "$cache" = MISS ] && [ "$p" = 120.0 ] || fail "tras escribir, la caché de Redis debería estar invalidada: $cache $p"
ok "escribir por catalog-1 BORRA la entrada de Redis: catalog-2 lee el precio nuevo ($p)"

read_ $C1 INV-002 local >/dev/null; read_ $C2 INV-002 local >/dev/null
price $C1 INV-002 79.00
read -r c1 _ p1 <<<"$(read_ $C1 INV-002 local)"; read -r c2 _ p2 <<<"$(read_ $C2 INV-002 local)"
[ "$p1" = 79.0 ] && [ "$c2" = HIT ] && [ "$p2" = 89.0 ] || fail "caché local: catalog-1 $c1 $p1, catalog-2 $c2 $p2"
ok "caché LOCAL con 2 instancias → catalog-1 ve $p1, catalog-2 sigue con $p2 (HIT de un dato viejo, hasta que venza el TTL)"
price $C1 INV-002 89.00; curl -fsS -o /dev/null -X DELETE "$C2/admin/cache/INV-002"

stampede() { # protección → consultas a la base
  protection "$1"; evict INV-001; reset_stats
  for i in $(seq 1 10); do curl -s -o /dev/null "$C1/api/inventions/INV-001?cache=redis" & curl -s -o /dev/null "$C2/api/inventions/INV-001?cache=redis" & done; wait
  db_queries
}
without=$(stampede false); with=$(stampede true)
[ "$without" -ge 5 ] && [ "$with" = 1 ] || fail "stampede: sin protección $without consultas, con protección $with"
ok "stampede: 20 lecturas a la vez de una clave vencida → $without consultas a la base sin protección, $with con el candado en Redis"

echo "🎉 Lab 13 OK"
