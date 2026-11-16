package com.mercadoinventos.orders;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Lo que le pasó a un pedido, paso a paso. Es lo que la UI dibuja como línea de tiempo. */
@Entity
@Table(name = "order_timeline")
public class TimelineEntry extends PanacheEntity {

    @JsonIgnore
    public UUID orderId;
    public Instant at;
    public String eventType;
    public String description;
    /** false = el evento llegó pero no correspondía al estado (se ignoró). */
    public boolean applied;

    static void record(UUID orderId, String eventType, String description, boolean applied) {
        TimelineEntry entry = new TimelineEntry();
        entry.orderId = orderId;
        entry.at = Instant.now();
        entry.eventType = eventType;
        entry.description = description;
        entry.applied = applied;
        entry.persist();
    }

    static List<TimelineEntry> of(UUID orderId) {
        return list("orderId = ?1 order by id", orderId);
    }
}
