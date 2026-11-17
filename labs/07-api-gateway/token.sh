#!/usr/bin/env bash
# Pide un token a Keycloak para un usuario del realm "mercado" y lo imprime.
#
#   ./token.sh ana            # ana / ana   (rol buyer)
#   ./token.sh carla          # carla / carla (rol buyer)
#   ./token.sh beto           # beto / beto (sin rol buyer)
#   TOKEN=$(./token.sh ana); curl -H "Authorization: Bearer $TOKEN" localhost:8080/api/orders
#
# ⚠️ Usa el flujo "password" (Resource Owner Password Credentials) porque es cómodo desde
# la terminal. Una web real usa Authorization Code + PKCE: la contraseña nunca pasa por la app.
set -euo pipefail
USER_NAME="${1:?Uso: $0 <usuario>}"
curl -fsS http://localhost:8180/realms/mercado/protocol/openid-connect/token \
  -d client_id=web -d grant_type=password -d "username=$USER_NAME" -d "password=${2:-$USER_NAME}" \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['access_token'])"
