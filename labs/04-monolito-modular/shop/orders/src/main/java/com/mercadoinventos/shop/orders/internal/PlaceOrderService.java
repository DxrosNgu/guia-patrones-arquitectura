package com.mercadoinventos.shop.orders.internal;

import com.mercadoinventos.shop.catalog.api.CatalogApi;
import com.mercadoinventos.shop.catalog.api.Product;
import com.mercadoinventos.shop.inventory.api.InventoryApi;
import com.mercadoinventos.shop.orders.api.OrderPlaced;
import com.mercadoinventos.shop.payments.api.PaymentsApi;
import io.quarkus.hibernate.orm.panache.Panache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Hacer un pedido toca CUATRO módulos, y es UNA transacción local:
 * si el pago se rechaza, la reserva de stock se deshace sola (rollback).
 * Es la facilidad que se pierde al separar en microservicios (lab 05) y que
 * se recupera, con mucho más trabajo, con una Saga (labs 16 y 17).
 */
@ApplicationScoped
public class PlaceOrderService {

    @Inject
    CatalogApi catalog;      // ← solo interfaces públicas (paquetes ..api..)

    @Inject
    InventoryApi inventory;

    @Inject
    PaymentsApi payments;

    @Inject
    Event<OrderPlaced> orderPlaced;

    @Transactional
    public OrderEntity place(String customerId, Map<String, Integer> quantitiesBySku) {
        BigDecimal total = BigDecimal.ZERO;
        for (var item : quantitiesBySku.entrySet()) {
            Product product = catalog.findBySku(item.getKey())
                    .orElseThrow(() -> new UnknownProductException(item.getKey()));
            total = total.add(product.price().multiply(BigDecimal.valueOf(item.getValue())));
        }

        OrderEntity order = new OrderEntity();
        order.id = UUID.randomUUID();
        order.customerId = customerId;
        order.total = total;
        order.status = "CONFIRMED";
        order.createdAt = Instant.now();
        order.quantitiesBySku.putAll(quantitiesBySku);
        Panache.getEntityManager().persist(order);

        inventory.reserve(order.id, quantitiesBySku);        // OutOfStockException → rollback de todo
        payments.charge(order.id, customerId, total);        // PaymentDeclinedException → rollback de todo (¡stock incluido!)

        orderPlaced.fire(new OrderPlaced(order.id, customerId, total, Map.copyOf(quantitiesBySku)));
        return order;
    }
}
