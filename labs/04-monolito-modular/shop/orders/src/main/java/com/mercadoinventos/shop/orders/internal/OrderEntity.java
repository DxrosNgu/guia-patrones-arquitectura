package com.mercadoinventos.shop.orders.internal;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(schema = "orders", name = "orders")
public class OrderEntity {

    @Id
    public UUID id;
    public String customerId;
    public BigDecimal total;
    public String status;
    public Instant createdAt;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(schema = "orders", name = "order_lines", joinColumns = @JoinColumn(name = "order_id"))
    @MapKeyColumn(name = "sku")
    @Column(name = "quantity")
    public Map<String, Integer> quantitiesBySku = new HashMap<>();
}
