package com.mercadoinventos.orders.saga;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

/** Transactional Outbox (lab 15): el evento se guarda en la MISMA transacción que el cambio. */
@ApplicationScoped
public class Outbox {

    private final EntityManager em;

    Outbox(EntityManager em) {
        this.em = em;
    }

    public void enqueue(String topic, Envelope event) {
        em.createNativeQuery("INSERT INTO outbox (id, topic, key, payload) VALUES (?1, ?2, ?3, ?4)")
            .setParameter(1, event.eventId())
            .setParameter(2, topic)
            .setParameter(3, event.orderId())
            .setParameter(4, event.toJson())
            .executeUpdate();
    }
}
