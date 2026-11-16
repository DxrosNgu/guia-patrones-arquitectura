package com.mercadoinventos.shop.payments.internal;

import com.mercadoinventos.shop.payments.api.PaymentDeclinedException;
import com.mercadoinventos.shop.payments.api.PaymentsApi;
import io.quarkus.hibernate.orm.panache.Panache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
class PaymentsService implements PaymentsApi {

    /** Simula el límite de la tarjeta del cliente. */
    @ConfigProperty(name = "payments.approval-limit", defaultValue = "500.00")
    BigDecimal approvalLimit;

    @Override
    @Transactional(Transactional.TxType.MANDATORY)
    public void charge(UUID orderId, String customerId, BigDecimal amount) {
        if (amount.compareTo(approvalLimit) > 0) {
            throw new PaymentDeclinedException("Pago rechazado: " + amount + " supera el límite de " + approvalLimit);
        }
        PaymentEntity payment = new PaymentEntity();
        payment.id = UUID.randomUUID();
        payment.orderId = orderId;
        payment.customerId = customerId;
        payment.amount = amount;
        payment.createdAt = Instant.now();
        Panache.getEntityManager().persist(payment);
    }
}
