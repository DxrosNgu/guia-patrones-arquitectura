#!/usr/bin/env bash
# Reenvía un evento que YA existe en un topic, idéntico (mismo eventId): simula una
# reentrega de Kafka o un productor que reintentó (caso S7).
#   scripts/replay.sh <topic> <orderId> <tipo>
# Ejemplo:
#   scripts/replay.sh inventory.events 3f2a... StockReserved
set -euo pipefail
cd "$(dirname "$0")/.."

TOPIC="${1:?topic}"; ORDER="${2:?orderId}"; TYPE="${3:?tipo}"
LINE=$(docker compose exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic "$TOPIC" --from-beginning --timeout-ms 8000 --property print.key=true --property key.separator='|' 2>/dev/null \
  | grep "^$ORDER|" | grep -E "\"type\": ?\"$TYPE\"" | head -1 || true)
[ -n "$LINE" ] || { echo "No encontré $TYPE de $ORDER en $TOPIC"; exit 1; }
echo "$LINE" | docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic "$TOPIC" --property parse.key=true --property key.separator='|' >/dev/null
echo "Reenviado $TYPE de $ORDER (mismo eventId)"
