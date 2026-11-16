package com.mercadoinventos.orders;

import com.mercadoinventos.orders.SagaInstance.State;
import com.mercadoinventos.orders.saga.Envelope;
import com.mercadoinventos.orders.saga.Inbox;
import com.mercadoinventos.orders.saga.Outbox;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ORQUESTADOR de la saga "crear pedido".
 *
 * Todo el flujo está escrito AQUÍ, en un solo lugar:
 *
 *   RESERVING_STOCK ──StockReserved──▶ CHARGING ──PaymentCompleted──▶ SCHEDULING_SHIPMENT ──ShipmentScheduled──▶ APPROVED
 *        │                               │  (PIVOTE)                       │
 *        └─StockReservationFailed─▶ REJECTED   └─PaymentFailed─▶ compensar ReleaseStock ─▶ REJECTED
 *                                                                          └─ShipmentFailed─▶ compensar ReleaseStock + RefundPayment ─▶ CANCELLED
 *
 * Y lo que la coreografía no podía hacer:
 *   - TIMEOUTS: si un paso no responde a tiempo, se REENVÍA su comando (idempotente).
 *   - Antes del pivote, tras `saga.max-attempts` intentos se ABANDONA la saga y se compensa (EXPIRED).
 *   - Después del pivote NUNCA se compensa por timeout: se reintenta para siempre.
 *   - Las compensaciones también se reintentan hasta que respondan.
 */
@ApplicationScoped
public class OrderSagaOrchestrator {

    private static final Logger LOG = Logger.getLogger(OrderSagaOrchestrator.class);

    private final Inbox inbox;
    private final Outbox outbox;
    private final EntityManager em;
    private final Duration stepTimeout;
    private final int maxAttempts;

    OrderSagaOrchestrator(Inbox inbox, Outbox outbox, EntityManager em,
                          @ConfigProperty(name = "saga.step-timeout") Duration stepTimeout,
                          @ConfigProperty(name = "saga.max-attempts") int maxAttempts) {
        this.inbox = inbox;
        this.outbox = outbox;
        this.em = em;
        this.stepTimeout = stepTimeout;
        this.maxAttempts = maxAttempts;
    }

    // ── inicio ───────────────────────────────────────────────────────────

    @Transactional
    public Order start(String customerId, List<Order.Item> items) {
        Order order = Order.create(customerId, items);
        order.persist();
        SagaInstance saga = new SagaInstance();
        saga.orderId = order.id;
        moveTo(saga, State.RESERVING_STOCK, Instant.now());
        saga.persist();
        TimelineEntry.record(order.id, "OrderCreated", "Pedido creado: el orquestador inicia la saga", true);
        send(saga, order, "ReserveStock");
        LOG.infof("🛒 Pedido %s: saga iniciada", order.id);
        return order;
    }

    // ── respuestas de los participantes ─────────────────────────────────

    @Incoming("saga-in")
    @Blocking
    @Transactional
    public void on(String raw) {
        Envelope reply = Envelope.parse(raw);
        if (!inbox.firstTime(reply.eventId())) {
            return;
        }
        UUID orderId = UUID.fromString(reply.orderId());
        SagaInstance saga = em.find(SagaInstance.class, orderId, LockModeType.PESSIMISTIC_WRITE);
        if (saga == null) {
            return;
        }
        Order order = Order.findById(orderId);
        Instant now = Instant.now();
        String type = reply.type();
        String detail = detailOf(reply);

        switch (saga.state) {
            case RESERVING_STOCK -> {
                if (type.equals("StockReserved")) {
                    record(order, type, "Inventario reservó el stock", true);
                    moveTo(saga, State.CHARGING, now);
                    send(saga, order, "ChargePayment");
                    return;
                }
                if (type.equals("StockReservationFailed")) {
                    record(order, type, "Inventario no tiene stock" + detail, true);
                    finish(saga, order, State.REJECTED);
                    return;
                }
            }
            case CHARGING -> {
                if (type.equals("PaymentCompleted")) {
                    record(order, type, "Pagos cobró (PIVOTE: desde aquí solo se avanza)", true);
                    moveTo(saga, State.SCHEDULING_SHIPMENT, now);
                    send(saga, order, "ScheduleShipment");
                    return;
                }
                if (type.equals("PaymentFailed")) {
                    record(order, type, "Pagos rechazó el cobro" + detail, true);
                    compensate(saga, order, List.of("ReleaseStock"), State.REJECTED, now);
                    return;
                }
            }
            case SCHEDULING_SHIPMENT -> {
                if (type.equals("ShipmentScheduled")) {
                    record(order, type, "Envíos programó el despacho" + detail, true);
                    finish(saga, order, State.APPROVED);
                    return;
                }
                if (type.equals("ShipmentFailed")) {
                    record(order, type, "Envíos no puede despachar" + detail, true);
                    compensate(saga, order, List.of("ReleaseStock", "RefundPayment"), State.CANCELLED, now);
                    return;
                }
            }
            case COMPENSATING -> {
                String compensation = switch (type) {
                    case "StockReleased" -> "ReleaseStock";
                    case "PaymentRefunded" -> "RefundPayment";
                    default -> null;
                };
                List<String> pending = saga.pending();
                if (compensation != null && pending.remove(compensation)) {
                    record(order, type, "↩️ Compensación confirmada: " + compensation, true);
                    saga.pending(pending);
                    if (pending.isEmpty()) {
                        finish(saga, order, saga.outcome);
                    }
                    return;
                }
            }
            default -> {
            }
        }
        // La respuesta no corresponde al paso actual: llegó tarde, fuera de orden o ya no importa.
        record(order, type, "Respuesta ignorada: la saga estaba en " + saga.state, false);
        LOG.infof("Pedido %s: %s ignorado en %s", order.id, type, saga.state);
    }

    // ── cancelación del cliente (S11) ───────────────────────────────────

    @Transactional
    public Order cancel(UUID orderId) {
        SagaInstance saga = em.find(SagaInstance.class, orderId, LockModeType.PESSIMISTIC_WRITE);
        if (saga == null) {
            throw new jakarta.ws.rs.NotFoundException();
        }
        Order order = Order.findById(orderId);
        List<String> compensations = switch (saga.state) {
            case RESERVING_STOCK -> List.of("ReleaseStock");
            case CHARGING -> List.of("ReleaseStock", "RefundPayment");   // por si el cobro ya ocurrió
            default -> throw new NotCancellable(saga.state);
        };
        record(order, "OrderCancelled", "El cliente canceló: el orquestador compensa lo hecho", true);
        compensate(saga, order, compensations, State.CANCELLED, Instant.now());
        return order;
    }

    public static class NotCancellable extends RuntimeException {
        NotCancellable(State state) {
            super("No se puede cancelar en " + state + ": ya se pasó el pivote (el cobro) o la saga terminó");
        }
    }

    // ── timeouts (S6, S9, S13) ──────────────────────────────────────────

    @Scheduled(every = "1s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void tick() {
        checkTimeouts(Instant.now());
    }

    /** Revisa las sagas cuyo paso actual no respondió a tiempo. Público para poder probarlo. */
    public void checkTimeouts(Instant now) {
        QuarkusTransaction.requiringNew().run(() -> {
            @SuppressWarnings("unchecked")
            List<SagaInstance> late = em.createNativeQuery("""
                    SELECT * FROM saga_instance
                    WHERE deadline < :now AND state IN ('RESERVING_STOCK','CHARGING','SCHEDULING_SHIPMENT','COMPENSATING')
                    FOR UPDATE SKIP LOCKED""", SagaInstance.class)
                .setParameter("now", now).getResultList();
            for (SagaInstance saga : late) {
                onTimeout(saga, Order.findById(saga.orderId), now);
            }
        });
    }

    private void onTimeout(SagaInstance saga, Order order, Instant now) {
        long seconds = stepTimeout.toSeconds();
        switch (saga.state) {
            case RESERVING_STOCK, CHARGING -> {
                if (saga.attempts < maxAttempts) {
                    retry(saga, now);
                    record(order, "Timeout", "⏱ Sin respuesta en %ds: se reenvía %s (intento %d de %d)"
                        .formatted(seconds, commandOf(saga.state), saga.attempts, maxAttempts), true);
                    send(saga, order, commandOf(saga.state));
                } else {
                    record(order, "Timeout", "⌛ El participante no respondió tras %d intentos: se abandona la saga y se compensa"
                        .formatted(maxAttempts), true);
                    List<String> compensations = saga.state == State.CHARGING
                        ? List.of("ReleaseStock", "RefundPayment") : List.of("ReleaseStock");
                    compensate(saga, order, compensations, State.EXPIRED, now);
                }
            }
            case SCHEDULING_SHIPMENT -> {
                retry(saga, now);
                record(order, "Timeout", "⏱ Envíos no responde: después del pivote se REINTENTA, no se compensa (intento %d)"
                    .formatted(saga.attempts), true);
                send(saga, order, "ScheduleShipment");
            }
            case COMPENSATING -> {
                retry(saga, now);
                record(order, "Timeout", "⏱ Compensación sin respuesta: se reenvía %s (intento %d)"
                    .formatted(saga.pendingCompensations, saga.attempts), true);
                saga.pending().forEach(c -> send(saga, order, c));
            }
            default -> {
            }
        }
    }

    // ── piezas ──────────────────────────────────────────────────────────

    private void moveTo(SagaInstance saga, State state, Instant now) {
        saga.state = state;
        saga.attempts = 1;
        saga.deadline = now.plus(stepTimeout);
        saga.updatedAt = now;
    }

    private void retry(SagaInstance saga, Instant now) {
        saga.attempts++;
        saga.deadline = now.plus(stepTimeout);
        saga.updatedAt = now;
    }

    private void compensate(SagaInstance saga, Order order, List<String> compensations, State outcome, Instant now) {
        moveTo(saga, State.COMPENSATING, now);
        saga.outcome = outcome;
        saga.pending(compensations);
        compensations.forEach(c -> send(saga, order, c));
        LOG.infof("↩️  Pedido %s: compensando %s → %s", order.id, compensations, outcome);
    }

    private void finish(SagaInstance saga, Order order, State outcome) {
        saga.state = outcome;
        saga.deadline = null;
        saga.updatedAt = Instant.now();
        order.status = Order.Status.valueOf(outcome.name());
        order.updatedAt = saga.updatedAt;
        record(order, "Saga" + capitalize(outcome.name()), "Saga terminada: " + outcome, true);
        LOG.infof("🏁 Pedido %s: %s", order.id, outcome);
    }

    private static String commandOf(State state) {
        return switch (state) {
            case RESERVING_STOCK -> "ReserveStock";
            case CHARGING -> "ChargePayment";
            case SCHEDULING_SHIPMENT -> "ScheduleShipment";
            default -> throw new IllegalStateException(state.name());
        };
    }

    /** Cada comando va al topic de SU participante, con la key = id del pedido (orden garantizado). */
    private void send(SagaInstance saga, Order order, String command) {
        var items = order.items.stream().map(i -> Map.of("sku", i.sku, "quantity", i.quantity)).toList();
        switch (command) {
            case "ReserveStock" -> outbox.enqueue("inventory.commands",
                Envelope.create(command, order.id.toString(), Map.of("items", items)));
            case "ReleaseStock" -> outbox.enqueue("inventory.commands",
                Envelope.create(command, order.id.toString(), Map.of()));
            case "ChargePayment" -> outbox.enqueue("payments.commands", Envelope.create(command, order.id.toString(),
                Map.of("customerId", order.customerId, "total", order.total, "currency", order.currency)));
            case "RefundPayment" -> outbox.enqueue("payments.commands",
                Envelope.create(command, order.id.toString(), Map.of()));
            case "ScheduleShipment" -> outbox.enqueue("shipping.commands",
                Envelope.create(command, order.id.toString(), Map.of("items", items)));
            default -> throw new IllegalArgumentException(command);
        }
    }

    private static void record(Order order, String type, String description, boolean applied) {
        TimelineEntry.record(order.id, type, description, applied);
    }

    private static String detailOf(Envelope reply) {
        var reason = reply.payload().get("reason");
        var tracking = reply.payload().get("trackingCode");
        if (reason != null) {
            return " (" + reason.asText() + ")";
        }
        return tracking != null ? " (" + tracking.asText() + ")" : "";
    }

    private static String capitalize(String s) {
        return s.charAt(0) + s.substring(1).toLowerCase();
    }
}
