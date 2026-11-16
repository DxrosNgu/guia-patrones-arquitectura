package com.mercadoinventos.orders.ddd.domain;

import java.time.Instant;
import java.util.UUID;

/** Evento de dominio: el cliente confirmó su pedido. */
public record OrderConfirmed(UUID orderId, String customerId, Money total, int lineCount, Instant occurredAt)
        implements DomainEvent {
}
