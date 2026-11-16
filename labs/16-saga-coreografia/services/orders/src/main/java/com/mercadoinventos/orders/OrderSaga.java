package com.mercadoinventos.orders;

import com.mercadoinventos.orders.saga.Envelope;
import com.mercadoinventos.orders.saga.Inbox;
import com.mercadoinventos.orders.saga.Outbox;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * La saga vista desde pedidos (COREOGRAFÍA).
 *
 * Pedidos INICIA la saga (OrderCreated) y SIGUE su avance escuchando a los
 * demás, pero no da órdenes: cada participante decide solo qué hacer con los
 * eventos que le llegan. El flujo completo no está escrito en ningún lugar,
 * está repartido entre los cuatro servicios.
 */
@ApplicationScoped
public class OrderSaga {

    private static final Logger LOG = Logger.getLogger(OrderSaga.class);
    private static final String TOPIC = "orders.events";

    static final Map<String, String> DESCRIPTIONS = Map.ofEntries(
        Map.entry("OrderCreated", "Pedido creado: empieza la saga"),
        Map.entry("StockReserved", "Inventario reservó el stock"),
        Map.entry("StockReservationFailed", "Inventario no tiene stock"),
        Map.entry("StockReleased", "↩️ Compensación: inventario liberó el stock"),
        Map.entry("PaymentCompleted", "Pagos cobró el pedido (pivote: desde aquí solo se avanza)"),
        Map.entry("PaymentFailed", "Pagos rechazó el cobro"),
        Map.entry("PaymentRefunded", "↩️ Compensación: pagos reembolsó el cobro"),
        Map.entry("ShipmentScheduled", "Envíos programó el despacho"),
        Map.entry("ShipmentFailed", "Envíos no puede despachar: hay que compensar"),
        Map.entry("OrderCancelled", "El cliente canceló el pedido"));

    private final Inbox inbox;
    private final Outbox outbox;

    OrderSaga(Inbox inbox, Outbox outbox) {
        this.inbox = inbox;
        this.outbox = outbox;
    }

    @Transactional
    public Order start(String customerId, List<Order.Item> items) {
        Order order = Order.create(customerId, items);
        order.persist();
        outbox.enqueue(TOPIC, Envelope.create("OrderCreated", order.id.toString(), Map.of(
            "customerId", customerId,
            "items", items.stream().map(i -> Map.of("sku", i.sku, "quantity", i.quantity, "unitPrice", i.unitPrice)).toList(),
            "total", order.total,
            "currency", order.currency)));
        TimelineEntry.record(order.id, "OrderCreated", DESCRIPTIONS.get("OrderCreated"), true);
        LOG.infof("🛒 Pedido %s creado por %s (total %s)", order.id, customerId, order.total);
        return order;
    }

    @Incoming("saga-in")
    @Blocking
    @Transactional
    public void on(String raw) {
        Envelope event = Envelope.parse(raw);
        if (!DESCRIPTIONS.containsKey(event.type()) || !inbox.firstTime(event.eventId())) {
            return;
        }
        Order order = Order.findById(UUID.fromString(event.orderId()));
        if (order == null) {
            return;
        }
        String description = DESCRIPTIONS.get(event.type()) + reasonOf(event);

        if (!Order.TRANSITIONS.containsKey(event.type())) {
            // StockReleased, PaymentRefunded: no cambian el estado, pero se registran.
            TimelineEntry.record(order.id, event.type(), description, true);
            return;
        }
        Order.Status before = order.status;
        Optional<Order.Status> after = order.apply(event.type());
        if (after.isPresent()) {
            TimelineEntry.record(order.id, event.type(), description, true);
            LOG.infof("Pedido %s: %s → %s (%s)", order.id, before, after.get(), event.type());
        } else {
            // El evento no corresponde al estado actual (llegó tarde, duplicado lógico o fuera de orden).
            TimelineEntry.record(order.id, event.type(), description + " · ignorado: el pedido estaba " + before, false);
            LOG.infof("Pedido %s: %s ignorado en estado %s", order.id, event.type(), before);
        }
    }

    @Transactional
    public Order cancel(UUID orderId) {
        Order order = Order.findById(orderId);
        if (order == null) {
            throw new jakarta.ws.rs.NotFoundException();
        }
        if (!order.cancellable()) {
            throw new NotCancellable(order.status);
        }
        order.status = Order.Status.CANCELLED;
        outbox.enqueue(TOPIC, Envelope.create("OrderCancelled", order.id.toString(),
            Map.of("reason", "el cliente canceló")));
        TimelineEntry.record(order.id, "OrderCancelled", DESCRIPTIONS.get("OrderCancelled"), true);
        LOG.infof("🚫 Pedido %s cancelado por el cliente", order.id);
        return order;
    }

    public static class NotCancellable extends RuntimeException {
        NotCancellable(Order.Status status) {
            super("No se puede cancelar un pedido en estado " + status + ": ya se pasó el pivote (el cobro)");
        }
    }

    private static String reasonOf(Envelope event) {
        var reason = event.payload().get("reason");
        var tracking = event.payload().get("trackingCode");
        if (reason != null) {
            return " (" + reason.asText() + ")";
        }
        return tracking != null ? " (" + tracking.asText() + ")" : "";
    }
}
