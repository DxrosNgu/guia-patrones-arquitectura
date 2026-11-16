package com.mercadoinventos.orders;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Construye eventos con el envelope común de la guía. */
@ApplicationScoped
public class Events {

    private final ObjectMapper json;

    Events(ObjectMapper json) {
        this.json = json;
    }

    public OutboxEvent orderPlaced(Order order) {
        OutboxEvent event = new OutboxEvent();
        event.id = UUID.randomUUID();
        event.aggregateType = "orders";
        event.aggregateId = order.id.toString();
        event.type = "OrderPlaced";
        event.createdAt = Instant.now();

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", event.id.toString());
        envelope.put("type", event.type);
        envelope.put("aggregateId", event.aggregateId);
        envelope.put("occurredAt", event.createdAt.toString());
        envelope.put("payload", Map.of(
            "customerId", order.customerId,
            "items", order.items.stream().map(i -> Map.of("sku", i.sku, "quantity", i.quantity)).toList()));
        try {
            event.payload = json.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return event;
    }
}
