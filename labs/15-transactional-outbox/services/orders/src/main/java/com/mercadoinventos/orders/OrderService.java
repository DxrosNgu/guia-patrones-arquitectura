package com.mercadoinventos.orders;

import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class OrderService {

    private static final Logger LOG = Logger.getLogger(OrderService.class);

    private final String mode;
    private final Events events;
    private final KafkaSender kafka;
    private final Chaos chaos;

    OrderService(@ConfigProperty(name = "outbox.mode") String mode, Events events, KafkaSender kafka, Chaos chaos) {
        this.mode = mode;
        this.events = events;
        this.kafka = kafka;
        this.chaos = chaos;
    }

    public Order place(String customerId, List<Order.Item> items) {
        Order order = Order.create(customerId, items);
        order.status = Order.Status.PLACED;
        OutboxEvent event = events.orderPlaced(order);

        if ("naive".equals(mode)) {
            // ❌ DUAL WRITE: dos sistemas, dos operaciones, ninguna transacción común.
            QuarkusTransaction.requiringNew().run(order::persist);       // 1. base de datos
            chaos.afterCommit("el pedido");                              //    💥 si muere aquí...
            kafka.send(event.aggregateId, event.payload);                // 2. Kafka → ...esto nunca pasa
            LOG.infof("[naive] Pedido %s guardado y publicado", order.id);
        } else {
            // ✅ TRANSACTIONAL OUTBOX: pedido y evento en la MISMA transacción.
            //    O quedan los dos, o ninguno. Publicar es problema de otro (polling o Debezium).
            QuarkusTransaction.requiringNew().run(() -> {
                order.persist();
                event.persist();
            });
            chaos.afterCommit("el pedido y su evento");
            LOG.infof("[%s] Pedido %s guardado con su evento %s en la outbox", mode, order.id, event.id);
        }
        return order;
    }
}
