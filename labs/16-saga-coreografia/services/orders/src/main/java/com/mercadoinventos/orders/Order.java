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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order extends PanacheEntityBase {

    /**
     * PENDING es el "semantic lock" de la saga: el pedido existe, pero todavía no
     * está confirmado. Nadie debe tratarlo como definitivo hasta APPROVED.
     */
    public enum Status { PENDING, STOCK_RESERVED, PAID, APPROVED, REJECTED, CANCELLED }

    /** Máquina de estados: qué evento mueve el pedido de qué estado a cuál. */
    record Transition(Status from, Status to) {
    }

    static final Map<String, Transition> TRANSITIONS = Map.of(
        "StockReserved", new Transition(Status.PENDING, Status.STOCK_RESERVED),
        "StockReservationFailed", new Transition(Status.PENDING, Status.REJECTED),
        "PaymentCompleted", new Transition(Status.STOCK_RESERVED, Status.PAID),
        "PaymentFailed", new Transition(Status.STOCK_RESERVED, Status.REJECTED),
        "ShipmentScheduled", new Transition(Status.PAID, Status.APPROVED),
        "ShipmentFailed", new Transition(Status.PAID, Status.CANCELLED));

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

    /** @return el nuevo estado si el evento aplica; vacío si no corresponde al estado actual. */
    public Optional<Status> apply(String eventType) {
        Transition transition = TRANSITIONS.get(eventType);
        if (transition == null || transition.from() != status) {
            return Optional.empty();
        }
        status = transition.to();
        updatedAt = Instant.now();
        return Optional.of(status);
    }

    /** Se puede cancelar mientras no se haya pasado el PIVOTE (el cobro). */
    public boolean cancellable() {
        return status == Status.PENDING || status == Status.STOCK_RESERVED;
    }
}
