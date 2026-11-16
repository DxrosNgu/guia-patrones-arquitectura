package com.mercadoinventos.orders.ddd.domain;

/** DRAFT → CONFIRMED → PAID → SHIPPED; DRAFT o CONFIRMED → CANCELLED. */
public enum OrderStatus {
    DRAFT,
    CONFIRMED,
    PAID,
    SHIPPED,
    CANCELLED
}
