package com.mercadoinventos.inventory;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import java.util.List;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "orders")
@Path("/api/orders")
public interface OrdersClient {

    record OrderView(List<Item> items) {
    }

    @GET
    @Path("{id}")
    OrderView get(@PathParam("id") String id);
}
