package com.mercadoinventos.orders;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Discovery por DNS (el modelo de Docker y de los Service de Kubernetes): un NOMBRE, "inventory".
 * Docker resuelve ese nombre a las IPs de las réplicas. El cliente no sabe cuántas hay ni si están sanas.
 */
@RegisterRestClient(configKey = "inventory-dns")
@Path("/api/stock")
public interface InventoryViaDns {

    @GET
    @Path("{sku}")
    StockLevel stock(@PathParam("sku") String sku);
}
