package com.mercadoinventos.payments;

import com.mercadoinventos.payments.saga.Envelope;
import com.mercadoinventos.payments.saga.Inbox;
import com.mercadoinventos.payments.saga.Outbox;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Pagos como participante de una saga ORQUESTADA.
 *
 *   comando         hace                                  responde
 *   ─────────────   ───────────────────────────────────   ─────────────────────────────────
 *   ChargePayment   cobra (el PIVOTE)                     PaymentCompleted | PaymentFailed
 *   RefundPayment   compensa: reembolsa si hubo cobro     PaymentRefunded (siempre)
 *
 * Reconciliación (caso S6): si el orquestador no recibe respuesta a tiempo,
 * REENVÍA ChargePayment. Pagos busca si ya cobró ese pedido y responde lo que
 * pasó, en lugar de cobrar de nuevo. Nadie compensa "a ciegas".
 */
@ApplicationScoped
public class PaymentsParticipant {

    private static final Logger LOG = Logger.getLogger(PaymentsParticipant.class);
    private static final String REPLIES = "saga.replies";

    private final Inbox inbox;
    private final Outbox outbox;
    private final BigDecimal approvalLimit;
    private final Chaos chaos;

    PaymentsParticipant(Inbox inbox, Outbox outbox, Chaos chaos,
                        @ConfigProperty(name = "payments.approval-limit") BigDecimal approvalLimit) {
        this.inbox = inbox;
        this.outbox = outbox;
        this.chaos = chaos;
        this.approvalLimit = approvalLimit;
    }

    @Incoming("saga-in")
    @Blocking
    @Transactional
    public void on(String raw) {
        Envelope command = Envelope.parse(raw);
        if (!inbox.firstTime(command.eventId())) {
            return;
        }
        chaos.maybeDelay();   // 🐢 experimento S6: pagos tarda más que el timeout del orquestador
        switch (command.type()) {
            case "ChargePayment" -> charge(command);
            case "RefundPayment" -> refund(command.orderId());
            default -> { /* no es para pagos */ }
        }
    }

    private void charge(Envelope command) {
        String orderId = command.orderId();
        Optional<Payment> existing = Payment.ofOrder(orderId);
        if (existing.isPresent()) {
            LOG.infof("↩️  ChargePayment reenviado para %s: ya estaba %s, se responde lo mismo",
                orderId, existing.get().status);
            reply(existing.get());
            return;
        }
        Payment payment = new Payment();
        payment.id = UUID.randomUUID();
        payment.orderId = orderId;
        payment.customerId = command.payload().get("customerId").asText();
        payment.amount = command.payload().get("total").decimalValue();
        payment.currency = command.payload().get("currency").asText();
        payment.createdAt = Instant.now();
        if (payment.amount.compareTo(approvalLimit) <= 0) {
            payment.status = Payment.Status.APPROVED;
        } else {
            payment.status = Payment.Status.DECLINED;
            payment.reason = "Supera el límite de la tarjeta (" + approvalLimit.setScale(2) + ")";
        }
        payment.persist();
        LOG.infof("💳 Pedido %s: %s %s → %s", orderId, payment.amount, payment.currency, payment.status);
        reply(payment);
    }

    private void reply(Payment payment) {
        if (payment.status == Payment.Status.DECLINED) {
            outbox.enqueue(REPLIES, Envelope.create("PaymentFailed", payment.orderId, Map.of("reason", payment.reason)));
        } else {
            // APPROVED (o REFUNDED: el cobro sí ocurrió) → para el orquestador, el pago se completó.
            outbox.enqueue(REPLIES, Envelope.create("PaymentCompleted", payment.orderId, Map.of(
                "paymentId", payment.id.toString(), "amount", payment.amount)));
        }
    }

    private void refund(String orderId) {
        Optional<Payment> charged = Payment.ofOrder(orderId).filter(p -> p.status == Payment.Status.APPROVED);
        charged.ifPresent(p -> {
            p.status = Payment.Status.REFUNDED;
            p.reason = "Reembolsado por el orquestador";
            LOG.infof("↩️  Pedido %s: reembolso de %s", orderId, p.amount);
        });
        outbox.enqueue(REPLIES, Envelope.create("PaymentRefunded", orderId, Map.of("refunded", charged.isPresent())));
    }
}
