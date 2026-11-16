package com.mercadoinventos.payments;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** El "cobro" en sí. No sabe nada de idempotencia: eso lo agrega {@link IdempotentPayments}. */
@ApplicationScoped
public class PaymentService {

    private static final Logger LOG = Logger.getLogger(PaymentService.class);

    private final BigDecimal approvalLimit;

    PaymentService(@ConfigProperty(name = "payments.approval-limit") BigDecimal approvalLimit) {
        this.approvalLimit = approvalLimit;
    }

    /** Debe llamarse dentro de una transacción. */
    public Payment charge(PaymentResource.PaymentRequest request) {
        Payment payment = new Payment();
        payment.id = UUID.randomUUID();
        payment.orderId = request.orderId();
        payment.customerId = request.customerId();
        payment.amount = request.amount();
        payment.currency = request.currency();
        payment.createdAt = Instant.now();
        if (request.amount().compareTo(approvalLimit) <= 0) {
            payment.status = Payment.Status.APPROVED;
        } else {
            payment.status = Payment.Status.DECLINED;
            payment.reason = "Supera el límite de la tarjeta (" + approvalLimit.setScale(2) + ")";
        }
        payment.persist();
        LOG.infof("💳 COBRO %s del pedido %s: %s %s → %s", payment.id, payment.orderId,
            payment.amount, payment.currency, payment.status);
        return payment;
    }
}
