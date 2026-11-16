package com.mercadoinventos.orders;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox")
public class OutboxEvent extends PanacheEntityBase {

    @Id
    public UUID id;
    @Column(name = "aggregatetype")
    public String aggregateType;
    @Column(name = "aggregateid")
    public String aggregateId;
    public String type;
    public String payload;
    public Instant createdAt;
    public Instant publishedAt;
}
