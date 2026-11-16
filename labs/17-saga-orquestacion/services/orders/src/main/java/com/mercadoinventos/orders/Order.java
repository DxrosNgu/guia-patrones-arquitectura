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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order extends PanacheEntityBase {

    /** Lo que ve el cliente. El detalle del avance vive en {@link SagaInstance}. */
    public enum Status { PENDING, APPROVED, REJECTED, CANCELLED, EXPIRED }

    @Embeddable
    public static class Item {
        public String sku;
        public int quantity;
        public BigDecimal unitPrice;

        public Item() {
        }

        public Item(String sku, int quantity, BigDecimal unitPrice) {
            this.sku = sku;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }
    }

    @Id
    public UUID id;
    public String customerId;
    @Enumerated(EnumType.STRING)
    public Status status;
    public BigDecimal total;
    public String currency;
    public Instant createdAt;
    public Instant updatedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_items", joinColumns = @JoinColumn(name = "order_id"))
    public List<Item> items = new ArrayList<>();

    public static Order create(String customerId, List<Item> items) {
        Order order = new Order();
        order.id = UUID.randomUUID();
        order.customerId = customerId;
        order.status = Status.PENDING;
        order.currency = "PEN";
        order.items.addAll(items);
        order.total = items.stream()
            .map(i -> i.unitPrice.multiply(BigDecimal.valueOf(i.quantity)))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.createdAt = order.updatedAt = Instant.now();
        return order;
    }
}
