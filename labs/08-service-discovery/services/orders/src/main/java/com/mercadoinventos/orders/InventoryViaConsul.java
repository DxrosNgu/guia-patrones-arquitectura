package com.mercadoinventos.orders;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Client-side discovery: la URL es stork://inventory. En cada llamada, Stork le pregunta a Consul
 * qué instancias SANAS hay (con caché de unos segundos) y elige una con round-robin.
 */
@RegisterRestClient(configKey = "inventory-consul")
@Path("/api/stock")
public interface InventoryViaConsul {

    @GET
    @Path("{sku}")
    StockLevel stock(@PathParam("sku") String sku);
}
