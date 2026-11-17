package com.mercadoinventos.orders;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.panache.common.Sort;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/** El MISMO contrato que /api/orders del legado: el gateway puede mandar una petición a cualquiera de los dos. */
@Path("/api/orders")
public class OrderResource {

    public record PlaceOrder(String customer, String sku, int quantity) {
    }

    @Inject
    @RestClient
    Clients.Catalog catalog;

    @POST
    public Response place(PlaceOrder request) {
        if (request == null || request.customer() == null || request.quantity() < 1) {
            return Response.status(422).entity(Map.of("message", "Faltan datos")).build();
        }
        Clients.Product product;
        try {
            product = catalog.find(request.sku());
        } catch (WebApplicationException e) {
            return Response.status(422).entity(Map.of("message", "No existe " + request.sku())).build();
        }
        ShopOrder order = QuarkusTransaction.requiringNew().call(() -> {
            ShopOrder o = new ShopOrder();
            o.id = ((Number) ShopOrder.getEntityManager().createNativeQuery("SELECT nextval('order_ids')")
                .getSingleResult()).longValue();
            o.customer = request.customer();
            o.sku = request.sku();
            o.quantity = request.quantity();
            o.total = product.price().multiply(BigDecimal.valueOf(request.quantity()));
            o.createdAt = Instant.now();
            o.origin = ShopOrder.NEW;
            o.syncedToLegacy = false;
            o.persist();
            return o;
        });
        return Response.status(201).entity(OrderJson.of(order)).build();
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") long id) {
        ShopOrder order = ShopOrder.findById(id);
        return order == null ? Response.status(404).entity(Map.of("message", "No existe")).build()
            : Response.ok(OrderJson.of(order)).build();
    }

    @GET
    public List<OrderJson> byCustomer(@QueryParam("customer") String customer) {
        return ShopOrder.<ShopOrder>list("customer", Sort.descending("id"), customer).stream().map(OrderJson::of).toList();
    }
}
