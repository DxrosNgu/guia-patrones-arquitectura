package com.mercadoinventos.orders;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Flujo del pedido, pensado para producir una traza interesante:
 *
 *   POST /api/orders (orders)
 *     ├── orders.check-stock                 ← @WithSpan (StockCheck)
 *     │     └── GET /api/stock/{sku}  → inventory (HTTP, un span por ítem)
 *     └── orders.save-and-publish            ← Tracer, a mano (aquí)
 *           ├── INSERT orders         → Postgres  (JDBC)
 *           └── publish OrderPlaced   → Kafka     (el traceparent viaja en los headers)
 *                 └── inventory consume y reserva (otro proceso, otro lenguaje, la MISMA traza)
 *
 * Lo automático (HTTP, JDBC, Kafka) lo pone la extensión quarkus-opentelemetry.
 * Lo de negocio (qué paso del flujo es, qué pedido, cuántos se aceptan) lo agregamos nosotros.
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

    private static final AttributeKey<String> RESULT = AttributeKey.stringKey("result");

    private final StockCheck stock;
    private final OrderEvents events;
    private final Tracer tracer;
    private final LongCounter ordersPlaced;

    OrderService(StockCheck stock, OrderEvents events, Tracer tracer, Meter meter) {
        this.stock = stock;
        this.events = events;
        this.tracer = tracer;
        // Métrica de negocio (la técnica la pone Quarkus: http_server_requests...). En Prometheus: orders_placed_total.
        this.ordersPlaced = meter.counterBuilder("orders.placed")
            .setDescription("Pedidos recibidos, por resultado").setUnit("1").build();
    }

    public Order place(String customerId, List<Order.Item> items) {
        try {
            stock.ensureAvailable(items.size(), items);
        } catch (OutOfStock e) {
            ordersPlaced.add(1, Attributes.of(RESULT, "out_of_stock"));
            throw e;
        }

        Order order = Order.create(customerId, items);
        order.status = Order.Status.PLACED;

        // Instrumentación A MANO, forma 2: el Tracer. Más código que @WithSpan, pero control total:
        // atributos calculados, eventos, y qué hacer con cada error.
        Span span = tracer.spanBuilder("orders.save-and-publish")
            .setAttribute("order.id", order.id.toString())
            .startSpan();
        try (Scope ignored = span.makeCurrent()) {   // makeCurrent: lo de adentro (JDBC, Kafka) cuelga de este span
            QuarkusTransaction.requiringNew().run(order::persist);
            span.addEvent("order.saved");
            events.orderPlaced(order);
        } catch (RuntimeException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, "No se pudo guardar o publicar el pedido");
            throw e;
        } finally {
            span.end();                               // sin end(), el span nunca se exporta
        }

        // Atributo en el span de la petición: luego puedes buscar trazas por order.id en Grafana.
        Span.current().setAttribute("order.id", order.id.toString());
        ordersPlaced.add(1, Attributes.of(RESULT, "accepted"));
        LOG.infof("Pedido %s aceptado (%d ítems)", order.id, items.size());
        return order;
    }
}
