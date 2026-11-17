package com.mercadoinventos.orders;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import java.math.BigDecimal;
import java.util.List;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

public final class Clients {

    private Clients() {
    }

    public record Product(String sku, String name, BigDecimal price) {
    }

    /** El catálogo NUEVO. */
    @RegisterRestClient(configKey = "catalog")
    @Path("/api/catalog/products")
    public interface Catalog {

        @GET
        @Path("/{sku}")
        Product find(@PathParam("sku") String sku);
    }

    /** Lo que se le agregó al legado para la migración. Cuando el legado se apague, esto se borra. */
    @RegisterRestClient(configKey = "legacy")
    @Path("/internal/orders")
    public interface LegacyOrders {

        @GET
        List<OrderJson> createdAfter(@QueryParam("afterId") long afterId, @QueryParam("limit") int limit);

        @PUT
        @Path("/{id}")
        void store(@PathParam("id") long id, OrderJson order);
    }
}
