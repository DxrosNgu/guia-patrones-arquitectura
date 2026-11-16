package com.mercadoinventos.orders;

import com.mercadoinventos.orders.clients.InventoryClient;
import io.opentelemetry.api.trace.Span;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Flujo del pedido, pensado para producir una traza interesante:
 *
 *   POST /api/orders (orders)
 *     ├── GET /api/stock/{sku}  → inventory (HTTP, un span por ítem)
 *     ├── INSERT orders         → Postgres  (JDBC)
 *     └── publish OrderPlaced   → Kafka     (el traceparent viaja en los headers)
 *           └── inventory consume y reserva (otro proceso, otro lenguaje, la MISMA traza)
 */
@ApplicationScoped
public class OrderService {

    private static final Logger LOG = Logger.getLogger(OrderService.class);

    public static class OutOfStock extends RuntimeException {
        OutOfStock(String sku, int available) {
            super("Stock insuficiente para " + sku + ": hay " + available);
        }
    }

    public static class InventoryUnavailable extends RuntimeException {
        InventoryUnavailable(Throwable cause) {
            super("No pudimos consultar el stock: el servicio de inventario no responde", cause);
        }
    }

    private final InventoryClient inventory;
    private final OrderEvents events;

    OrderService(@RestClient InventoryClient inventory, OrderEvents events) {
        this.inventory = inventory;
        this.events = events;
    }

    public Order place(String customerId, List<Order.Item> items) {
        for (Order.Item item : items) {
            int available = availableStock(item.sku);
            if (available < item.quantity) {
                LOG.infof("Pedido rechazado: %s pide %d y hay %d", item.sku, item.quantity, available);
                throw new OutOfStock(item.sku, available);
            }
        }

        Order order = Order.create(customerId, items);
        order.status = Order.Status.PLACED;
        QuarkusTransaction.requiringNew().run(order::persist);
        events.orderPlaced(order);

        // Atributo propio en el span actual: luego puedes buscar trazas por order.id en Grafana.
        Span.current().setAttribute("order.id", order.id.toString());
        LOG.infof("Pedido %s aceptado (%d ítems)", order.id, items.size());
        return order;
    }

    private int availableStock(String sku) {
        try {
            return inventory.stock(sku).available();
        } catch (WebApplicationException e) {
            return 0; // el SKU no existe → no hay stock
        } catch (ProcessingException e) {
            LOG.warnf("Inventario no responde al consultar %s", sku);
            throw new InventoryUnavailable(e);
        }
    }
}
