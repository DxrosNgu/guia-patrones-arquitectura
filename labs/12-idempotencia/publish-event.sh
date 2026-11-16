#!/usr/bin/env bash
# Publica un evento OrderPlaced en orders.events (como lo haría el servicio de pedidos).
#
#   ./publish-event.sh <eventId> <orderId> <sku> <cantidad>
#   ./publish-event.sh evt-1 pedido-1 INV-001 2
#
# Publicar DOS veces el mismo eventId simula una reentrega de Kafka.
set -euo pipefail

EVENT_ID="${1:?eventId}"; ORDER_ID="${2:?orderId}"; SKU="${3:-INV-001}"; QTY="${4:-1}"
EVENT="{\"eventId\":\"$EVENT_ID\",\"type\":\"OrderPlaced\",\"aggregateId\":\"$ORDER_ID\",\"occurredAt\":\"$(date -u +%FT%TZ)\",\"payload\":{\"customerId\":\"ana\",\"items\":[{\"sku\":\"$SKU\",\"quantity\":$QTY}]}}"

# key|valor: la key es el id del pedido, igual que en el servicio real.
echo "$ORDER_ID|$EVENT" | docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic orders.events \
  --property parse.key=true --property key.separator='|' > /dev/null

echo "Publicado $EVENT_ID (pedido $ORDER_ID, $QTY × $SKU)"
