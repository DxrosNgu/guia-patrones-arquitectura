package com.mercadoinventos.payments;

/** Lo que respondió la pasarela, o PENDING_CONFIRMATION si no se pudo saber (fallback). */
public record ChargeResult(String chargeId, String status) {

    static ChargeResult pendingConfirmation() {
        return new ChargeResult(null, "PENDING_CONFIRMATION");
    }
}
