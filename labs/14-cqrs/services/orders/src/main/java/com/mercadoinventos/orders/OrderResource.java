package com.mercadoinventos.orders;

import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * El lado de COMANDOS: solo escribe. Fíjate que no hay GET de listados ni filtros:
 * consultar es trabajo del lado de lectura (order-history).
 */
@Path("/api/orders")
public class OrderResource {

    public record ItemRequest(String sku, int quantity) {
    }

    public record PlaceOrder(String customerId, List<ItemRequest> items) {
    }

    @Inject
    OrderEvents events;

    @POST
    public Response place(PlaceOrder request) {
        if (request == null || request.customerId() == null || request.items() == null || request.items().isEmpty()) {
            throw new BadRequestException("Faltan customerId o items");
        }
        Order order = new Order();
        order.id = UUID.randomUUID();
        order.customerId = request.customerId();
        order.status = "PLACED";
        order.createdAt = Instant.now();
        order.version = 1;
        BigDecimal total = BigDecimal.ZERO;
        for (ItemRequest requested : request.items()) {
            Catalog.Product product = Catalog.find(requested.sku()).orElse(null);
            if (product == null || requested.quantity() < 1) {
                return Response.status(422).entity(Map.of("message", "Producto o cantidad inválidos: " + requested.sku())).build();
            }
            Order.Item item = new Order.Item();
            item.sku = requested.sku();
            item.name = product.name();
            item.quantity = requested.quantity();
            item.unitPrice = product.price();
            order.items.add(item);
            total = total.add(product.price().multiply(BigDecimal.valueOf(requested.quantity())));
        }
        order.total = total;
        QuarkusTransaction.requiringNew().run(order::persist);
        events.orderPlaced(order);
        // La respuesta incluye la versión: el cliente puede pedirle al lado de lectura "como mínimo esta versión".
        return Response.status(201).entity(Map.of("id", order.id, "status", order.status, "version", order.version,
            "total", order.total)).build();
    }
}
