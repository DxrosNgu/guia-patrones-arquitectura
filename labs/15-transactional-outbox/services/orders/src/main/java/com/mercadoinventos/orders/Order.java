package com.mercadoinventos.orders;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order extends PanacheEntityBase {

    /**
     * PLACED    → aceptado; inventario lo procesará cuando lea el evento (modo async)
     * CONFIRMED → inventario reservó el stock (modo sync)
     * REJECTED  → inventario no tenía stock (modo sync)
     */
    public enum Status { PLACED, CONFIRMED, REJECTED }

    @Embeddable
    public static class Item {
        public String sku;
        public int quantity;

        public Item() {
        }

        public Item(String sku, int quantity) {
            this.sku = sku;
            this.quantity = quantity;
        }
    }

    @Id
    public UUID id;
    public String customerId;
    @Enumerated(EnumType.STRING)
    public Status status;
    public Instant createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_items", joinColumns = @JoinColumn(name = "order_id"))
    public List<Item> items = new ArrayList<>();

    public static Order create(String customerId, List<Item> items) {
        Order order = new Order();
        order.id = UUID.randomUUID();
        order.customerId = customerId;
        order.createdAt = Instant.now();
        order.items.addAll(items);
        return order;
    }
}
