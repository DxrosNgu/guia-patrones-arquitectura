#!/usr/bin/env bash
# Prueba de humo del lab 02: el MISMO contrato contra las tres arquitecturas,
# y las hexagonales cambiando de adaptador (Postgres → memoria) sin recompilar.
set -euo pipefail
cd "$(dirname "$0")"

fail() { echo "❌ $1"; exit 1; }
ok()   { echo "✅ $1"; }

wait_for() { # url
  for _ in $(seq 1 90); do curl -fsS -o /dev/null "$1" 2>/dev/null && return 0; sleep 2; done
  fail "$1 no responde"
}

post() { # url json → "código cuerpo"
  curl -s -o /tmp/lab02-body -w "%{http_code}" -X POST "$1" -H 'Content-Type: application/json' -d "$2"
}

contract() { # base nombre
  local base="$1" name="$2" inventor="smoke-$RANDOM-$RANDOM" code id
  wait_for "$base/api/info"

  code=$(post "$base/api/inventions" "{\"inventorId\":\"$inventor\",\"name\":\"Gratis\",\"price\":0}")
  [ "$code" = 422 ] && grep -q PRICE_MUST_BE_POSITIVE /tmp/lab02-body || fail "$name: precio 0 debería dar 422 ($code)"

  code=$(post "$base/api/inventions" "{\"inventorId\":\"$inventor\",\"name\":\"Paraguas solar\",\"price\":49.9}")
  [ "$code" = 201 ] || fail "$name: no creó el borrador ($code)"
  id=$(grep -o '"id":"[^"]*"' /tmp/lab02-body | cut -d'"' -f4)

  code=$(post "$base/api/inventions/$id/publish" "")
  [ "$code" = 422 ] && grep -q IMAGE_REQUIRED /tmp/lab02-body || fail "$name: publicar sin imagen debería dar 422 ($code)"

  curl -fsS -o /dev/null -X PUT "$base/api/inventions/$id/image" -H 'Content-Type: application/json' \
       -d '{"imageUrl":"https://img.example/paraguas.png"}' || fail "$name: no cambió la imagen"
  code=$(post "$base/api/inventions/$id/publish" "")
  [ "$code" = 200 ] && grep -q '"PUBLISHED"' /tmp/lab02-body || fail "$name: no publicó ($code)"

  code=$(post "$base/api/inventions" "{\"inventorId\":\"$inventor\",\"name\":\" paraguas SOLAR \",\"price\":1}")
  [ "$code" = 409 ] && grep -q NAME_ALREADY_USED /tmp/lab02-body || fail "$name: nombre repetido debería dar 409 ($code)"

  for n in A B C; do post "$base/api/inventions" "{\"inventorId\":\"$inventor\",\"name\":\"$n\",\"price\":1}" >/dev/null; done
  code=$(post "$base/api/inventions" "{\"inventorId\":\"$inventor\",\"name\":\"D\",\"price\":1}")
  [ "$code" = 409 ] && grep -q TOO_MANY_DRAFTS /tmp/lab02-body || fail "$name: el 4.º borrador debería dar 409 ($code)"

  ok "$name cumple el contrato ($(curl -fsS "$base/api/info"))"
  LAST_INVENTOR="$inventor"
}

contract http://localhost:8101 "capas (Quarkus)"
contract http://localhost:8111 "hexagonal (Quarkus)"
contract http://localhost:8112 "hexagonal (Python)"

# Segundo adaptador de entrada: el CLI lee lo que creó la API REST, con los mismos casos de uso.
docker compose exec -T catalog-hexagonal-py python -m app.adapters.cli list --inventor "$LAST_INVENTOR" \
  | grep -q '"Paraguas solar"' || fail "el CLI no ve el invento creado por REST"
ok "CLI (otro adaptador de entrada) lista el invento creado por REST"

# Cambiar de adaptador de salida sin recompilar: solo una variable de entorno.
CATALOG_PERSISTENCE=memory docker compose up -d --wait catalog-hexagonal catalog-hexagonal-py >/dev/null 2>&1
for port in 8111 8112; do
  wait_for "http://localhost:$port/api/info"
  curl -fsS "http://localhost:$port/api/info" | grep -q '"persistence":"memory"' \
    || fail ":$port no cambió al adaptador en memoria"
done
contract http://localhost:8111 "hexagonal (Quarkus) en memoria"
contract http://localhost:8112 "hexagonal (Python) en memoria"
docker compose up -d --wait catalog-hexagonal catalog-hexagonal-py >/dev/null 2>&1
ok "de vuelta a Postgres"

echo "🎉 Lab 02 OK"
