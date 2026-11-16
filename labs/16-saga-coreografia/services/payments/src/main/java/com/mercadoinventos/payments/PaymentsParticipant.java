package com.mercadoinventos.payments;

import com.mercadoinventos.payments.saga.Envelope;
import com.mercadoinventos.payments.saga.Inbox;
import com.mercadoinventos.payments.saga.Outbox;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Pagos como participante de la saga por COREOGRAFÍA.
 *
 *   escucha          hace                                       publica
 *   ──────────────   ────────────────────────────────────────   ─────────────────────────────────
 *   StockReserved    cobra (el PIVOTE de la saga)               PaymentCompleted | PaymentFailed
 *   ShipmentFailed   COMPENSA: reembolsa                        PaymentRefunded
 *   OrderCancelled   recuerda la cancelación; si ya cobró,      PaymentRefunded (si había cobro)
 *                    reembolsa
 *
 * Inbox + cambio + outbox en UNA transacción, igual que los participantes en Python.
 */
@ApplicationScoped
public class PaymentsParticipant {

    private static final Logger LOG = Logger.getLogger(PaymentsParticipant.class);
    private static final String TOPIC = "payments.events";

    private final Inbox inbox;
    private final Outbox outbox;
    private final EntityManager em;
    private final BigDecimal approvalLimit;

    PaymentsParticipant(Inbox inbox, Outbox outbox, EntityManager em,
                        @ConfigProperty(name = "payments.approval-limit") BigDecimal approvalLimit) {
        this.inbox = inbox;
        this.outbox = outbox;
        this.em = em;
        this.approvalLimit = approvalLimit;
    }

    @Incoming("saga-in")
    @Blocking
    @Transactional
    public void on(String raw) {
        Envelope event = Envelope.parse(raw);
        switch (event.type()) {
            case "StockReserved", "ShipmentFailed", "OrderCancelled" -> {
                if (!inbox.firstTime(event.eventId())) {
                    LOG.infof("↩️  %s %s ya procesado", event.type(), event.eventId());
                    return;
                }
                switch (event.type()) {
                    case "StockReserved" -> charge(event);
                    case "ShipmentFailed" -> refund(event.orderId(), "envío imposible");
                    default -> cancel(event.orderId());
                }
            }
            default -> { /* no le interesa a pagos */ }
        }
    }

    private void charge(Envelope event) {
        String orderId = event.orderId();
        if (isCancelled(orderId)) {
            LOG.infof("Pedido %s ya cancelado: no se cobra", orderId);
            return;
        }
        if (Payment.ofOrder(orderId).isPresent()) {
            return;   // ya hay un cobro para este pedido (otra forma de idempotencia)
        }
        Payment payment = new Payment();
        payment.id = UUID.randomUUID();
        payment.orderId = orderId;
        payment.customerId = event.payload().get("customerId").asText();
        payment.amount = event.payload().get("total").decimalValue();
        payment.currency = event.payload().get("currency").asText();
        payment.createdAt = Instant.now();

        if (payment.amount.compareTo(approvalLimit) <= 0) {
            payment.status = Payment.Status.APPROVED;
            payment.persist();
            outbox.enqueue(TOPIC, Envelope.create("PaymentCompleted", orderId, Map.of(
                "paymentId", payment.id.toString(), "amount", payment.amount, "currency", payment.currency,
                "items", event.payload().get("items"))));
            LOG.infof("💳 Pedido %s: cobro de %s %s APROBADO", orderId, payment.amount, payment.currency);
        } else {
            payment.status = Payment.Status.DECLINED;
            payment.reason = "Supera el límite de la tarjeta (" + approvalLimit.setScale(2) + ")";
            payment.persist();
            outbox.enqueue(TOPIC, Envelope.create("PaymentFailed", orderId, Map.of("reason", payment.reason)));
            LOG.infof("💳 Pedido %s: cobro RECHAZADO (%s)", orderId, payment.reason);
        }
    }

    /** Compensación idempotente: solo reembolsa un pago APROBADO. */
    private void refund(String orderId, String reason) {
        Payment.ofOrder(orderId)
            .filter(p -> p.status == Payment.Status.APPROVED)
            .ifPresent(payment -> {
                payment.status = Payment.Status.REFUNDED;
                payment.reason = "Reembolsado: " + reason;
                outbox.enqueue(TOPIC, Envelope.create("PaymentRefunded", orderId, Map.of(
                    "paymentId", payment.id.toString(), "amount", payment.amount, "reason", reason)));
                LOG.infof("↩️  Pedido %s: reembolso de %s (%s)", orderId, payment.amount, reason);
            });
    }

    private void cancel(String orderId) {
        em.createNativeQuery("INSERT INTO cancellations (order_id) VALUES (?1) ON CONFLICT DO NOTHING")
            .setParameter(1, orderId).executeUpdate();
        refund(orderId, "pedido cancelado");
    }

    private boolean isCancelled(String orderId) {
        return !em.createNativeQuery("SELECT 1 FROM cancellations WHERE order_id = ?1")
            .setParameter(1, orderId).getResultList().isEmpty();
    }
}
