#!/usr/bin/env bash
# Publica un evento escrito a mano (con un eventId NUEVO).
#   scripts/publish.sh <topic> <tipo> <orderId> ['{"reason":"..."}']
# Ejemplo (S8, evento fuera de orden):
#   scripts/publish.sh shipping.events ShipmentScheduled 3f2a... '{"trackingCode":"TRK-FALSO"}'
set -euo pipefail
cd "$(dirname "$0")/.."

TOPIC="${1:?topic}"; TYPE="${2:?tipo}"; ORDER="${3:?orderId}"; PAYLOAD="${4:-{\}}"
EVENT="{\"eventId\":\"$(cat /proc/sys/kernel/random/uuid 2>/dev/null || uuidgen)\",\"type\":\"$TYPE\",\"aggregateId\":\"$ORDER\",\"occurredAt\":\"$(date -u +%FT%TZ)\",\"payload\":$PAYLOAD}"
echo "$ORDER|$EVENT" | docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic "$TOPIC" --property parse.key=true --property key.separator='|' >/dev/null
echo "Publicado $TYPE para $ORDER en $TOPIC"
