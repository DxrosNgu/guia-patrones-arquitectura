package com.mercadoinventos.orders.clients;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

/**
 * Pedidos habla con inventario SOLO por su API pública.
 * No sabe qué tablas tiene inventario, ni en qué base viven.
 */
@RegisterRestClient(configKey = "inventory")
@Path("/api/reservations")
public interface InventoryClient {

    record Item(String sku, int quantity) {
    }

    record ReservationRequest(String orderId, List<Item> items) {
    }

    record ReservationResult(String orderId, String status) {
    }

    @POST
    ReservationResult reserve(ReservationRequest request);
}
