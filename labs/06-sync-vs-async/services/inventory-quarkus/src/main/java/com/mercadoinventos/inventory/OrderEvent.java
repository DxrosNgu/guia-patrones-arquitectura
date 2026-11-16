package com.mercadoinventos.inventory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

/** El envelope de los eventos de pedidos: { eventId, type, aggregateId, occurredAt, payload }. */
public record OrderEvent(String eventId, String type, String aggregateId, JsonNode payload) {

    private static final ObjectMapper JSON = new ObjectMapper();

    public static OrderEvent parse(String raw) {
        JsonNode node;
        try {
            node = JSON.readTree(raw);
        } catch (JsonProcessingException e) {
            throw new InvalidEventException("No es JSON válido: " + e.getOriginalMessage());
        }
        if (node == null || !node.isObject()) {
            throw new InvalidEventException("El evento debe ser un objeto JSON");
        }
        List<String> missing = new ArrayList<>();
        for (String field : List.of("eventId", "type", "aggregateId", "payload")) {
            if (!node.hasNonNull(field)) {
                missing.add(field);
            }
        }
        if (!missing.isEmpty()) {
            throw new InvalidEventException("Faltan campos del envelope: " + String.join(", ", missing));
        }
        return new OrderEvent(node.get("eventId").asText(), node.get("type").asText(),
                node.get("aggregateId").asText(), node.get("payload"));
    }

    /** Los ítems, si el evento los trae (Event-Carried State Transfer). Vacío si es Event Notification. */
    public List<Item> items() {
        JsonNode items = payload.get("items");
        if (items == null || items.isNull()) {
            return null;
        }
        List<Item> result = new ArrayList<>();
        items.forEach(i -> result.add(new Item(i.path("sku").asText(), i.path("quantity").asInt())));
        return result;
    }
}
