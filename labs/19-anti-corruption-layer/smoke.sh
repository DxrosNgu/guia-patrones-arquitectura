#!/usr/bin/env bash
# Prueba de humo del lab 19: el ACL traduce en las dos direcciones, convierte errores del legado en errores
# del dominio y aísla el cambio de un código; la versión ingenua deja pasar todo.
set -euo pipefail
cd "$(dirname "$0")"

S=http://localhost:8105
LEGACY=http://localhost:8121
fail() { echo "❌ $1"; exit 1; }
ok()   { echo "✅ $1"; }
wait_for() { for _ in $(seq 1 90); do curl -fsS -o /dev/null "$1" 2>/dev/null && return 0; sleep 2; done; fail "$1 no responde"; }
json() { python3 -c "import json,sys; d=json.load(sys.stdin); print($1)"; }
legacy_version() { curl -fsS -o /dev/null -X PUT "$LEGACY/admin/version" -H 'Content-Type: application/json' -d "{\"version\":$1}"; }
trap 'legacy_version 1 2>/dev/null || true' EXIT
create() { # distrito dirección kg → código http (cuerpo en /tmp/lab19-body)
  curl -s -o /tmp/lab19-body -w '%{http_code}' -X POST "$S/api/shipments" -H 'Content-Type: application/json' \
    -d "{\"orderId\":\"o-$RANDOM\",\"weightKg\":\"$3\",\"serviceLevel\":\"EXPRESS\",\"district\":\"$1\",\"address\":\"$2\"}"
}
naive_create() { # PESO_LB DIR → cuerpo
  curl -fsS -X POST "$S/api/naive/shipments" -H 'Content-Type: application/json' \
    -d "{\"orderId\":\"o-$RANDOM\",\"TPO_SRV\":\"EXP\",\"PESO_LB\":\"$1\",\"UBIGEO\":\"150122\",\"DIR\":\"$2\"}"
}
advance() { for _ in $(seq 1 "$2"); do curl -fsS -o /dev/null -X POST "$LEGACY/admin/guias/$1/avanzar"; done; }

wait_for "$S/health"; wait_for "$LEGACY/health"
legacy_version 1

# 1. Con ACL: la API habla el idioma del dominio, en las dos direcciones.
[ "$(create "Santiago de Surco" "Jr. Peña Ñaña 123, dpto. 4" 2.00)" = 201 ] || fail "crear envío: $(cat /tmp/lab19-body)"
id=$(json 'd["id"]' < /tmp/lab19-body); guide=$(json 'd["trackingCode"]' < /tmp/lab19-body)
python3 -c "import json; d=json.load(open('/tmp/lab19-body')); assert not {'EST','FEC_EST','PESO_LB','UBIGEO','TPO_SRV','COD_RET'} & set(d)" \
  || fail "la respuesta con ACL tiene campos del legado"
ok "con ACL: POST con kg, distrito y dirección con tildes → el traductor mandó libras, UBIGEO y MAYÚSCULAS; responde $guide, status CREATED, sin un solo campo del legado"

# 2. Los errores del legado (HTTP 200 + COD_RET) se vuelven errores del dominio.
[ "$(create "Arequipa" "Calle Mercaderes 100" 2.00)" = 422 ] && [ "$(json 'd["code"]' < /tmp/lab19-body)" = DESTINATION_NOT_COVERED ] || fail "destino sin cobertura"
[ "$(create "Miraflores" "Av. Larco 1150" 60.00)" = 422 ] && [ "$(json 'd["code"]' < /tmp/lab19-body)" = WEIGHT_LIMIT_EXCEEDED ] || fail "peso excedido"
naive=$(naive_create "132,28" "AV LARCO 1150" | json 'd["COD_RET"], d["DES_RET"]')
[ "$naive" = "07 PESO EXCEDE LIMITE" ] || fail "ingenua, peso: $naive"
ok "errores: con ACL → 422 DESTINATION_NOT_COVERED y 422 WEIGHT_LIMIT_EXCEEDED; sin ACL → HTTP 200 con COD_RET=07 (quien llama tiene que saber qué es 07)"

# 3. La versión ingenua arma el XML con un f-string.
[ "$(create "Miraflores" "Av. Larco & Pardo 300" 1.00)" = 201 ] || fail "ACL con &"
naive=$(naive_create "2,20" "AV LARCO & PARDO 300" | json 'd["COD_RET"], d["DES_RET"]')
[ "$naive" = "01 XML MAL FORMADO" ] || fail "ingenua con &: $naive"
ok "dirección con '&': con ACL → 201; sin ACL → COD_RET=01 XML MAL FORMADO"

# 4. Estados: el código del legado nunca sale del adaptador.
advance "$guide" 3
body=$(curl -fsS "$S/api/shipments/$id")
[ "$(echo "$body" | json 'd["status"], d["canRateDelivery"], d["carrierNote"]')" = "DELIVERED True RECIBIDO POR: SRA. MUÑOZ" ] || fail "entregado: $body"
naive=$(curl -fsS "$S/api/naive/shipments/$guide" | json 'd["EST"], d["puedeCalificar"]')
[ "$naive" = "4 True" ] || fail "ingenua entregado: $naive"
ok "entregado: con ACL → DELIVERED, canRateDelivery, nota con Ñ (ISO-8859-1 bien leído); sin ACL → EST=4"

docker compose exec -T shipping python -m app.acl.contract_check >/dev/null || fail "el contract check debería pasar con el legado v1"
ok "contract check contra el legado v1 → pasa"

# 5. El legado sube a la versión 2: 'EN RUTA' (3) ahora es 'EN TRANSITO' (7) y aparece 'EN REPARTO' (8).
legacy_version 2
create "Miraflores" "Av. Larco 1150" 1.00 >/dev/null
id2=$(json 'd["id"]' < /tmp/lab19-body); guide2=$(json 'd["trackingCode"]' < /tmp/lab19-body)
advance "$guide2" 2
acl=$(curl -fsS "$S/api/shipments/$id2" | json 'd["status"]')
naive=$(curl -fsS "$S/api/naive/shipments/$guide2" | json 'd["EST"], d["DES_EST"]')
[ "$acl" = UNKNOWN ] && [ "$naive" = "7 EN TRANSITO" ] || fail "legado v2: ACL $acl, ingenua $naive"
docker compose logs shipping | grep -q "sin traducir: EST=7" || fail "falta el WARNING del adaptador"
ok "legado v2: con ACL → status UNKNOWN + WARNING 'EST=7 sin traducir' (el 7 no entra al dominio); sin ACL → EST=7 llega a quien llama"

if out=$(docker compose exec -T shipping python -m app.acl.contract_check); then fail "el contract check debería fallar con el legado v2"; fi
echo "$out" | grep -q "EST=7" || fail "el contract check no nombra el código nuevo: $out"
ok "contract check contra el legado v2 → falla y dice qué cambió: $(echo "$out" | grep '✖' | head -1 | sed 's/✖ //')"

echo "🎉 Lab 19 OK"
