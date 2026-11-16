package com.mercadoinventos.inventory;

import io.quarkus.hibernate.orm.panache.Panache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;

/**
 * Todo o nada: si falta stock de un ítem, no se descuenta ninguno (igual que la versión Python).
 * El rechazo también queda registrado, en otra transacción.
 * ⚠️ Si el mismo evento llega dos veces, se reserva dos veces: lo resuelve el lab 12.
 */
@ApplicationScoped
public class ReservationService {

    static class Rejected extends RuntimeException {
        Rejected(String reason) {
            super(reason);
        }
    }

    @Inject
    RejectionLog rejections;

    public String reserve(String orderId, List<Item> items) {
        try {
            reserveAll(orderId, items);
            return "RESERVED";
        } catch (Rejected rejected) {
            rejections.record(orderId, items, rejected.getMessage());
            return "REJECTED";
        }
    }

    @Transactional
    void reserveAll(String orderId, List<Item> items) {
        var em = Panache.getEntityManager();
        for (Item item : items) {
            int updated = em.createNativeQuery(
                            "UPDATE stock SET available = available - ?1 WHERE sku = ?2 AND available >= ?1")
                    .setParameter(1, item.quantity()).setParameter(2, item.sku()).executeUpdate();
            if (updated == 0) {
                throw new Rejected("Stock insuficiente o SKU desconocido: " + item.sku()); // → rollback
            }
            em.createNativeQuery("INSERT INTO reservations (order_id, sku, quantity, status) VALUES (?1, ?2, ?3, 'RESERVED')")
                    .setParameter(1, orderId).setParameter(2, item.sku()).setParameter(3, item.quantity())
                    .executeUpdate();
        }
    }

    @ApplicationScoped
    static class RejectionLog {
        @Transactional
        void record(String orderId, List<Item> items, String reason) {
            var em = Panache.getEntityManager();
            for (Item item : items) {
                em.createNativeQuery("INSERT INTO reservations (order_id, sku, quantity, status, reason) "
                                + "VALUES (?1, ?2, ?3, 'REJECTED', ?4)")
                        .setParameter(1, orderId).setParameter(2, item.sku()).setParameter(3, item.quantity())
                        .setParameter(4, reason).executeUpdate();
            }
        }
    }
}
