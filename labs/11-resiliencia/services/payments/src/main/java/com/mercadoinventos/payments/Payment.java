package com.mercadoinventos.payments;

import java.math.BigDecimal;
import java.time.Instant;

public record Payment(String orderId, BigDecimal amount, String status, String chargeId, Instant updatedAt) {

    Payment with(String newStatus, String newChargeId) {
        return new Payment(orderId, amount, newStatus, newChargeId, Instant.now());
    }
}
