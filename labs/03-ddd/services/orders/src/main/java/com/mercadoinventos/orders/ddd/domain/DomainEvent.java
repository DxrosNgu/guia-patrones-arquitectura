package com.mercadoinventos.orders.ddd.domain;

import java.time.Instant;

/** Algo que pasó en el dominio y que a otros les puede interesar. Siempre en pasado. */
public interface DomainEvent {

    Instant occurredAt();
}
