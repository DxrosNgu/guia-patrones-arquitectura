package com.mercadoinventos.payments;

import java.math.BigDecimal;

/** Lo que se le manda a la pasarela. La idempotencyKey hace que reintentar no cobre dos veces (lab 12). */
public record ChargeRequest(String idempotencyKey, String orderId, BigDecimal amount) {
}
