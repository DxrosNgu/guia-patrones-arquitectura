package com.mercadoinventos.payments;

import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pagos de VARIOS pedidos en una sola llamada (?orderIds=a,b,c).
 * Sin esto, quien compone haría una llamada por pedido: el problema N+1.
 */
@Path("/api/payments")
public class PaymentResource {

    public record Payment(String orderId, String status, BigDecimal amount, String method, Instant paidAt) {
    }

    static final List<Payment> PAYMENTS = List.of(
        new Payment("ord-1001", "PAID", new BigDecimal("99.80"), "CARD", Instant.parse("2026-11-02T10:16:00Z")),
        new Payment("ord-1002", "PAID", new BigDecimal("120.00"), "CARD", Instant.parse("2026-11-12T16:41:00Z")),
        new Payment("ord-1003", "PENDING", new BigDecimal("76.50"), "TRANSFER", null),
        new Payment("ord-2001", "REFUNDED", new BigDecimal("89.00"), "CARD", Instant.parse("2026-11-08T12:01:00Z")));

    @Inject
    Chaos chaos;

    @GET
    public List<Payment> byOrders(@QueryParam("orderIds") String orderIds) {
        if (orderIds == null || orderIds.isBlank()) {
            throw new BadRequestException("Falta orderIds");
        }
        Set<String> ids = Arrays.stream(orderIds.split(",")).map(String::trim).collect(Collectors.toSet());
        chaos.pause();
        return PAYMENTS.stream().filter(p -> ids.contains(p.orderId())).toList();
    }
}
