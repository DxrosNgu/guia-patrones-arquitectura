package com.mercadoinventos.shop.inventory.internal;

import com.mercadoinventos.shop.inventory.api.InventoryApi;
import com.mercadoinventos.shop.inventory.api.OutOfStockException;
import io.quarkus.hibernate.orm.panache.Panache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
class InventoryService implements InventoryApi {

    @Override
    @Transactional(Transactional.TxType.MANDATORY) // siempre dentro de la transacción de quien llama
    public void reserve(UUID orderId, Map<String, Integer> quantitiesBySku) {
        var em = Panache.getEntityManager();
        quantitiesBySku.forEach((sku, quantity) -> {
            int updated = em.createNativeQuery(
                            "UPDATE inventory.stock SET available = available - ?1 WHERE sku = ?2 AND available >= ?1")
                    .setParameter(1, quantity).setParameter(2, sku).executeUpdate();
            if (updated == 0) {
                throw new OutOfStockException(sku);
            }
            em.createNativeQuery("INSERT INTO inventory.reservations (order_id, sku, quantity) VALUES (?1, ?2, ?3)")
                    .setParameter(1, orderId).setParameter(2, sku).setParameter(3, quantity).executeUpdate();
        });
    }
}
