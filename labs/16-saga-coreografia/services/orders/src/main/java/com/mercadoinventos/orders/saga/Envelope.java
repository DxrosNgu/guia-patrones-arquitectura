package com.mercadoinventos.orders.saga;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** El envelope común de los eventos de la guía. */
public record Envelope(String eventId, String type, String orderId, JsonNode payload) {

    private static final ObjectMapper JSON = new ObjectMapper();

    public static Envelope parse(String raw) {
        try {
            JsonNode node = JSON.readTree(raw);
            for (String field : new String[]{"eventId", "type", "aggregateId", "payload"}) {
                if (!node.hasNonNull(field)) {
                    throw new IllegalArgumentException("Falta el campo " + field);
                }
            }
            return new Envelope(node.get("eventId").asText(), node.get("type").asText(),
                node.get("aggregateId").asText(), node.get("payload"));
        } catch (Exception e) {
            throw new IllegalArgumentException("Evento inválido: " + e.getMessage(), e);
        }
    }

    public static Envelope create(String type, String orderId, Map<String, ?> payload) {
        return new Envelope(UUID.randomUUID().toString(), type, orderId, JSON.valueToTree(payload));
    }

    public String toJson() {
        ObjectNode node = JSON.createObjectNode();
        node.put("eventId", eventId);
        node.put("type", type);
        node.put("aggregateId", orderId);
        node.put("occurredAt", Instant.now().toString());
        node.set("payload", payload);
        return node.toString();
    }
}
