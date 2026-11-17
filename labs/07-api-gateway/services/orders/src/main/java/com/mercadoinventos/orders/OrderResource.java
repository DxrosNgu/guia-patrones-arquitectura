package com.mercadoinventos.orders;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.logging.Logger;

/**
 * Pedidos detrás del gateway. Aunque el gateway ya validó el token, este servicio lo valida
 * OTRA VEZ (quarkus-oidc): si alguien llega directo, saltándose el gateway, igual lo frena.
 * Los pedidos se guardan en memoria: este lab es sobre el gateway, no sobre persistencia.
 */
@Path("/api/orders")
public class OrderResource {

    private static final Logger LOG = Logger.getLogger(OrderResource.class);

    public record Item(String sku, int quantity) {
    }

    public record PlaceOrder(List<Item> items) {
    }

    public record Order(UUID id, String customer, List<Item> items, Instant createdAt) {
    }

    private static final Map<UUID, Order> ORDERS = new ConcurrentHashMap<>();

    @Inject
    SecurityIdentity identity;

    @POST
    @RolesAllowed("buyer")
    public Response place(PlaceOrder request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new BadRequestException("El pedido necesita al menos un ítem");
        }
        // El cliente es QUIEN FIRMA EL TOKEN, nunca un campo del cuerpo que cualquiera puede inventar.
        String customer = identity.getPrincipal().getName();
        Order order = new Order(UUID.randomUUID(), customer, List.copyOf(request.items()), Instant.now());
        ORDERS.put(order.id(), order);
        LOG.infof("Pedido %s de %s (%d ítems)", order.id(), customer, order.items().size());
        return Response.status(201).entity(order).build();
    }

    @GET
    @Authenticated
    public List<Order> mine() {
        String customer = identity.getPrincipal().getName();
        return ORDERS.values().stream().filter(o -> o.customer().equals(customer)).toList();
    }
}
