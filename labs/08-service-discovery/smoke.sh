#!/usr/bin/env bash
# Prueba de humo del lab 08: DNS vs. Consul + Stork, con una réplica enferma y otra muerta.
set -euo pipefail
cd "$(dirname "$0")"

ORDERS=http://localhost:8102
CONSUL=http://localhost:8500
fail() { echo "❌ $1"; exit 1; }
ok()   { echo "✅ $1"; }
wait_for() { for _ in $(seq 1 90); do curl -fsS -o /dev/null "$1" 2>/dev/null && return 0; sleep 2; done; fail "$1 no responde"; }
passing() { curl -fsS "$CONSUL/v1/health/service/inventory?passing" | python3 -c "import json,sys; print(len(json.load(sys.stdin)))"; }
wait_passing() { for _ in $(seq 1 30); do [ "$(passing)" = "$1" ] && return 0; sleep 1; done; fail "Consul debería tener $1 instancias sanas (tiene $(passing))"; }
spread() { curl -fsS "$ORDERS/api/discovery/$1/spread?calls=${2:-9}"; }
field() { python3 -c "import json,sys; d=json.load(sys.stdin); print($1)"; }
set_health() { # contenedor true|false
  docker exec "$1" python -c "import urllib.request; urllib.request.urlopen(urllib.request.Request('http://localhost:8000/admin/health', data=b'{\"healthy\": $2}', headers={'Content-Type': 'application/json'}))"
}

docker compose up -d --no-build --scale inventory=3 --wait >/dev/null 2>&1
wait_for "$ORDERS/q/health/ready"
wait_passing 3
ok "3 réplicas de inventario se registraron solas en Consul y pasan su health check"

s=$(spread consul)
[ "$(field 'len(d["byInstance"])' <<<"$s")" = 3 ] && [ "$(field 'd["errors"]' <<<"$s")" = 0 ] || fail "Stork debería repartir entre las 3: $s"
ok "Consul + Stork: 9 llamadas repartidas en round-robin → $(field 'd["byInstance"]' <<<"$s")"

s=$(spread dns)
ok "DNS: 9 llamadas → $(field 'd["byInstance"]' <<<"$s") (la conexión se reutiliza: casi siempre la misma réplica)"

sick=$(curl -fsS "$ORDERS/api/discovery/dns/stock/INV-001" | field 'd["instance"]')
set_health "$sick" false
wait_passing 2
sleep 3   # el refresh de Stork (2 s)
s=$(spread consul)
grep -q "\"$sick\"" <<<"$s" && fail "Stork no debería mandar nada a la réplica enferma: $s"
ok "réplica $sick enferma → Consul la saca y Stork la evita: $(field 'd["byInstance"]' <<<"$s")"
s=$(spread dns)
grep -q "\"$sick\"" <<<"$s" || fail "se esperaba que el DNS siguiera llegando a la réplica enferma: $s"
ok "…pero por DNS se le sigue llamando: $(field 'd["byInstance"]' <<<"$s") (el DNS no sabe de salud)"
set_health "$sick" true
wait_passing 3

victim=$(docker compose ps -q inventory | cut -c1-12 | grep -v "^$sick" | head -1)
docker kill "$victim" >/dev/null
wait_passing 2
sleep 3
s=$(spread consul)
[ "$(field 'd["errors"]' <<<"$s")" = 0 ] && ! grep -q "\"$victim\"" <<<"$s" || fail "tras el kill -9, Stork no debería llamar a $victim: $s"
ok "kill -9 de $victim → Consul la marca critical y Stork deja de llamarla: $(field 'd["byInstance"]' <<<"$s")"

docker compose up -d --no-build --scale inventory=4 --wait >/dev/null 2>&1
wait_passing 4
sleep 3
s=$(spread consul 8)
[ "$(field 'len(d["byInstance"])' <<<"$s")" = 4 ] || fail "con 4 réplicas, Stork debería usar las 4: $s"
ok "escalar a 4 → la nueva se registra sola y Stork la usa: $(field 'd["byInstance"]' <<<"$s")"
docker compose up -d --no-build --scale inventory=3 --wait >/dev/null 2>&1

echo "🎉 Lab 08 OK — mira http://localhost:8500/ui/dc1/services/inventory/instances"
