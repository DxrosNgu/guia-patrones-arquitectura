package com.mercadoinventos.inventory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import java.util.List;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class OrdersApiItemsSource implements OrderItemsSource {

    @RestClient
    OrdersClient orders;

    @Override
    public List<Item> itemsOf(String orderId) {
        try {
            return orders.get(orderId).items();
        } catch (ProcessingException | WebApplicationException e) {
            // Pedidos caído o respondiendo con error: puede resolverse solo → se reintenta.
            throw new TransientException("No pude pedirle el pedido " + orderId + " a orders: " + e.getMessage());
        }
    }
}
