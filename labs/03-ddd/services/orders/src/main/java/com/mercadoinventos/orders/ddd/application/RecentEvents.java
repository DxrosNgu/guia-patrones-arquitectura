package com.mercadoinventos.orders.ddd.application;

import com.mercadoinventos.orders.ddd.domain.DomainEvent;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Los últimos eventos de dominio, en memoria, para verlos en GET /api/events. Solo didáctico. */
@ApplicationScoped
public class RecentEvents {

    private static final int MAX = 50;
    private final Deque<DomainEvent> events = new ArrayDeque<>();

    public synchronized void add(DomainEvent event) {
        events.addFirst(event);
        if (events.size() > MAX) {
            events.removeLast();
        }
    }

    public synchronized List<DomainEvent> list() {
        return List.copyOf(events);
    }
}
