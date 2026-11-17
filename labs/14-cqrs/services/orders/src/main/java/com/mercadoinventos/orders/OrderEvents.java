package com.mercadoinventos.orders;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.reactive.messaging.kafka.Record;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

/**
 * Publica OrderPlaced con TODO lo que el lado de lectura necesita (Event-Carried State Transfer):
 * nombres, precios y total. Así la proyección no tiene que llamar a nadie.
 * ⚠️ Se publica después de guardar (dual write). La forma segura es un outbox (lab 15).
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
        payload.put("createdAt", order.createdAt.toString());
        payload.put("items", order.items.stream().map(i -> Map.of(
            "sku", i.sku, "name", i.name, "quantity", i.quantity, "unitPrice", i.unitPrice)).toList());
        payload.put("total", order.total);

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", UUID.randomUUID().toString());
        envelope.put("type", "OrderPlaced");
        envelope.put("aggregateId", order.id.toString());
        envelope.put("version", order.version);
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("payload", payload);
        try {
            emitter.send(Record.of(order.id.toString(), json.writeValueAsString(envelope)))
                .toCompletableFuture().orTimeout(Duration.ofSeconds(10).toMillis(), TimeUnit.MILLISECONDS).join();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
