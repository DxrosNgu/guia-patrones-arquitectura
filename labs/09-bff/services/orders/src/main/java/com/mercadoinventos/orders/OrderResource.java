package com.mercadoinventos.orders;

import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Pedidos: su API está pensada para el DOMINIO, no para ninguna pantalla.
 * Devuelve todo lo que sabe de un pedido; quien lo use se quedará con lo que necesite.
 */
@Path("/api/orders")
public class OrderResource {

    public record Item(String sku, String name, int quantity, BigDecimal unitPrice) {
    }

    public record Order(String id, String customerId, Instant createdAt, List<Item> items, BigDecimal total,
                        String status) {
    }

    static final List<Order> ORDERS = List.of(
        new Order("ord-1001", "ana", Instant.parse("2026-11-02T10:15:00Z"),
            List.of(new Item("INV-001", "Paraguas solar", 2, new BigDecimal("49.90"))), new BigDecimal("99.80"), "PLACED"),
        new Order("ord-1002", "ana", Instant.parse("2026-11-12T16:40:00Z"),
            List.of(new Item("INV-003", "Mochila con ventilador", 1, new BigDecimal("120.00"))), new BigDecimal("120.00"), "PLACED"),
        new Order("ord-1003", "ana", Instant.parse("2026-11-16T09:05:00Z"),
            List.of(new Item("INV-004", "Taza que avisa cuando se enfría", 3, new BigDecimal("25.50"))), new BigDecimal("76.50"), "PLACED"),
        new Order("ord-2001", "beto", Instant.parse("2026-11-08T12:00:00Z"),
            List.of(new Item("INV-002", "Lámpara de agua salada", 1, new BigDecimal("89.00"))), new BigDecimal("89.00"), "CANCELLED"));

    @Inject
    Chaos chaos;

    @GET
    public List<Order> byCustomer(@QueryParam("customerId") String customerId) {
        if (customerId == null || customerId.isBlank()) {
            throw new BadRequestException("Falta customerId");
        }
        chaos.pause();
        return ORDERS.stream().filter(o -> o.customerId().equals(customerId)).toList();
    }
}
