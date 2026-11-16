package com.mercadoinventos.orders;

import com.mercadoinventos.orders.clients.InventoryClient;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class OrderService {

    private static final Logger LOG = Logger.getLogger(OrderService.class);

    /** Inventario no respondió: en modo síncrono, el pedido no se puede tomar. */
    public static class InventoryUnavailable extends RuntimeException {
        InventoryUnavailable(Throwable cause) {
            super("El servicio de inventario no responde: no podemos tomar tu pedido ahora", cause);
        }
    }

    private final String mode;
    private final InventoryClient inventory;
    private final OrderEvents events;

    OrderService(@ConfigProperty(name = "orders.mode") String mode,
                 @RestClient InventoryClient inventory, OrderEvents events) {
        this.mode = mode;
        this.inventory = inventory;
        this.events = events;
    }

    public boolean isAsync() {
        return "async".equals(mode);
    }

    public Order place(String customerId, List<Order.Item> items) {
        Order order = Order.create(customerId, items);
        return isAsync() ? placeAsync(order) : placeSync(order);
    }

    /**
     * SÍNCRONO: pedidos depende de que inventario esté vivo AHORA MISMO
     * (acoplamiento temporal). Si inventario cae, nadie puede comprar.
     */
    private Order placeSync(Order order) {
        try {
            inventory.reserve(new InventoryClient.ReservationRequest(order.id.toString(),
                order.items.stream().map(i -> new InventoryClient.Item(i.sku, i.quantity)).toList()));
            order.status = Order.Status.CONFIRMED;
        } catch (WebApplicationException e) {
            order.status = Order.Status.REJECTED;
        } catch (ProcessingException e) {
            throw new InventoryUnavailable(e);
        }
        QuarkusTransaction.requiringNew().run(order::persist);
        LOG.infof("[sync] Pedido %s → %s", order.id, order.status);
        return order;
    }

    /**
     * ASÍNCRONO: pedidos solo necesita su base y Kafka. Inventario procesará
     * el evento cuando pueda, aunque ahora esté caído.
     *
     * ⚠️ Guardar en la base y publicar en Kafka son DOS operaciones (dual write).
     *    Si el proceso muere entre ambas, queda un pedido sin evento. El lab 15
     *    (Transactional Outbox) resuelve exactamente eso.
     */
    private Order placeAsync(Order order) {
        order.status = Order.Status.PLACED;
        QuarkusTransaction.requiringNew().run(order::persist);
        events.orderPlaced(order);
        LOG.infof("[async] Pedido %s aceptado y publicado", order.id);
        return order;
    }
}
