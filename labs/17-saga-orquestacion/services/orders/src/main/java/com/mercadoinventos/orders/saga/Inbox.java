package com.mercadoinventos.orders.saga;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

/** Idempotent Consumer (lab 12): true solo la primera vez que se ve un eventId. */
@ApplicationScoped
public class Inbox {

    private final EntityManager em;

    Inbox(EntityManager em) {
        this.em = em;
    }

    /** Debe llamarse dentro de la transacción que procesa el evento. */
    public boolean firstTime(String eventId) {
        return em.createNativeQuery(
                "INSERT INTO processed_messages (event_id) VALUES (?1) ON CONFLICT (event_id) DO NOTHING")
            .setParameter(1, eventId)
            .executeUpdate() == 1;
    }
}
