package com.mercadoinventos.orders.clients;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Consulta de stock antes de aceptar el pedido (validación rápida para el usuario).
 * La llamada aparece en la traza como un span "cliente" de orders y otro "servidor" de inventory.
 */
@RegisterRestClient(configKey = "inventory")
@Path("/api/stock")
public interface InventoryClient {

    record StockLevel(String sku, int available) {
    }

    @GET
    @Path("/{sku}")
    StockLevel stock(@PathParam("sku") String sku);
}
