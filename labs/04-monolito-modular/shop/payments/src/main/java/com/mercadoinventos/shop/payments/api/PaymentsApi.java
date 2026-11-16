package com.mercadoinventos.shop.payments.api;

import java.math.BigDecimal;
import java.util.UUID;

/** API PÚBLICA del módulo Pagos. */
public interface PaymentsApi {

    /** Cobra o lanza {@link PaymentDeclinedException}. Se une a la transacción de quien llama. */
    void charge(UUID orderId, String customerId, BigDecimal amount);
}
