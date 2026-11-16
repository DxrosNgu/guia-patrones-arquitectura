package com.mercadoinventos.orders;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.reactive.messaging.kafka.Record;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publica los eventos de pedidos en el topic orders.events.
 *
 * Todos los eventos de la guía usan el mismo "envelope":
 *   { eventId, type, aggregateId, occurredAt, payload }
 * y la KEY del mensaje es el id del pedido. Kafka garantiza el orden solo
 * dentro de una partición, y todos los eventos con la misma key van a la
 * misma partición: así los eventos de UN pedido nunca se desordenan.
 */
@ApplicationScoped
public class OrderEvents {

    private final Emitter<Record<String, String>> emitter;
    private final ObjectMapper json;

    OrderEvents(@Channel("orders-events") Emitter<Record<String, String>> emitter, ObjectMapper json) {
        this.emitter = emitter;
        this.json = json;
    }

    public void orderPlaced(Order order) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("customerId", order.customerId);
        payload.put("items", order.items.stream()
            .map(i -> Map.of("sku", i.sku, "quantity", i.quantity)).toList());
        // El traceparent NO va en el JSON: SmallRye lo agrega a los headers de Kafka automáticamente.
        publish("OrderPlaced", order.id, payload);
    }

    private void publish(String type, UUID aggregateId, Map<String, Object> payload) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", UUID.randomUUID().toString());
        envelope.put("type", type);
        envelope.put("aggregateId", aggregateId.toString());
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("payload", payload);
        try {
            emitter.send(Record.of(aggregateId.toString(), json.writeValueAsString(envelope)))
                .toCompletableFuture()
                // Esperamos la confirmación de Kafka: si no llega, el pedido falla visiblemente.
                .orTimeout(Duration.ofSeconds(10).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .join();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
