package com.mercadoinventos.inventory;

import io.smallrye.common.annotation.Blocking;
import io.smallrye.faulttolerance.api.BeforeRetry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

/**
 * El MISMO consumidor que services/inventory (Python + aiokafka), escrito con Quarkus Messaging.
 * Compara: aquí casi todo es configuración (application.properties) y anotaciones.
 *
 *  - Se procesa bien           → el método termina → SmallRye confirma el offset (ack).
 *  - Mensaje veneno            → InvalidEventException → nack → failure-strategy=dead-letter-queue.
 *  - Falla transitoria         → @Retry lo reintenta (cada 2 s, sin límite) ANTES de que llegue al nack.
 *                                 Mientras tanto, los mensajes siguientes esperan (procesamiento en orden).
 */
@ApplicationScoped
public class OrderEventsConsumer {

    private static final Logger LOG = Logger.getLogger(OrderEventsConsumer.class);

    @Inject
    ReservationService reservations;

    @Inject
    OrderItemsSource orders;

    @Incoming("orders-events")
    @Blocking                                  // usa la base (bloqueante): fuera del event loop, en orden
    @Retry(maxRetries = -1, delay = 2, delayUnit = ChronoUnit.SECONDS,
           maxDuration = 0, abortOn = InvalidEventException.class)
    @BeforeRetry(LogRetryHandler.class)        // sin esto, @Retry reintenta en silencio
    public void consume(String raw) {
        OrderEvent event = OrderEvent.parse(raw);
        if (!"OrderPlaced".equals(event.type())) {
            return;
        }
        List<Item> items = event.items();
        if (items == null) {
            // Event Notification: hay que pedirle los ítems a pedidos. Si está caído → TransientException → @Retry.
            items = orders.itemsOf(event.aggregateId());
        }
        String status = reservations.reserve(event.aggregateId(), items);
        LOG.infof("[quarkus] Pedido %s → %s", event.aggregateId(), status);
    }
}
