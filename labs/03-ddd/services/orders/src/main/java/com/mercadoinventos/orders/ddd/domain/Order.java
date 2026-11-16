package com.mercadoinventos.orders.ddd.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Raíz del agregado Pedido. Es la ÚNICA puerta para cambiar el pedido y sus líneas,
 * así que es el único lugar donde hay que proteger las invariantes:
 * <ul>
 *   <li>el total es siempre la suma de las líneas (se calcula, nunca se asigna),</li>
 *   <li>las líneas solo se editan mientras el pedido es un borrador,</li>
 *   <li>máximo 10 unidades por invento (lo garantiza {@link Quantity}),</li>
 *   <li>todas las líneas en la moneda del pedido,</li>
 *   <li>las transiciones de estado siguen el ciclo de vida.</li>
 * </ul>
 * No hay setters: cada cambio es un método con nombre de negocio.
 * <p>
 * ⚠️ Atajo pragmático: el agregado lleva anotaciones de JPA. En el lab 02 viste cómo separarlas.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;
    private String customerId;
    private Currency currency;
    @Enumerated(EnumType.STRING)
    private OrderStatus status;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_lines", joinColumns = @JoinColumn(name = "order_id"))
    @OrderColumn(name = "position")
    private List<OrderLine> lines = new ArrayList<>();
    private Instant createdAt;
    /** Concurrencia optimista: el agregado es la unidad de consistencia (ver "Rómpelo"). */
    @Version
    private long version;

    @Transient
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    protected Order() {
        // para JPA
    }

    public static Order create(String customerId, String currency) {
        if (customerId == null || customerId.isBlank()) {
            throw new DomainException("CUSTOMER_REQUIRED", "Falta el cliente");
        }
        Order order = new Order();
        order.id = UUID.randomUUID();
        order.customerId = customerId;
        order.currency = parseCurrency(currency);
        order.status = OrderStatus.DRAFT;
        order.createdAt = Instant.now();
        return order;
    }

    // ── Comportamiento ────────────────────────────────────────────────────

    public void addLine(String sku, Money unitPrice, Quantity quantity) {
        ensureEditable();
        Money.zero(currency).requireSameCurrency(unitPrice);
        Optional<OrderLine> existing = findLine(sku);
        if (existing.isPresent()) {
            // Mismo invento: se suma a la línea existente. Si supera 10, Quantity lo rechaza ANTES de cambiar nada.
            OrderLine merged = existing.get().withMore(quantity);
            lines.set(lines.indexOf(existing.get()), merged);
        } else {
            lines.add(new OrderLine(sku, unitPrice, quantity));
        }
    }

    public void removeLine(String sku) {
        ensureEditable();
        OrderLine line = findLine(sku)
                .orElseThrow(() -> new DomainException("LINE_NOT_FOUND", "El pedido no tiene el invento " + sku));
        lines.remove(line);
    }

    public void confirm() {
        requireStatus(OrderStatus.DRAFT, "confirmar");
        if (lines.isEmpty()) {
            throw new DomainException("EMPTY_ORDER", "No se puede confirmar un pedido sin líneas");
        }
        status = OrderStatus.CONFIRMED;
        domainEvents.add(new OrderConfirmed(id, customerId, total(), lines.size(), Instant.now()));
    }

    public void pay() {
        requireStatus(OrderStatus.CONFIRMED, "pagar");
        status = OrderStatus.PAID;
    }

    public void ship() {
        requireStatus(OrderStatus.PAID, "enviar");
        status = OrderStatus.SHIPPED;
    }

    public void cancel() {
        if (status != OrderStatus.DRAFT && status != OrderStatus.CONFIRMED) {
            throw new DomainException("INVALID_TRANSITION", "No se puede cancelar un pedido " + status);
        }
        status = OrderStatus.CANCELLED;
    }

    /** El total NO es un campo: se deriva de las líneas cada vez. Así no puede quedar desactualizado. */
    public Money total() {
        return lines.stream().map(OrderLine::subtotal).reduce(Money.zero(currency), Money::plus);
    }

    /** Entrega los eventos registrados y los olvida (los publica el servicio de aplicación). */
    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    // ── Reglas internas ───────────────────────────────────────────────────

    private void ensureEditable() {
        if (status != OrderStatus.DRAFT) {
            throw new DomainException("ORDER_NOT_EDITABLE", "Un pedido " + status + " ya no se puede modificar");
        }
    }

    private void requireStatus(OrderStatus expected, String action) {
        if (status != expected) {
            throw new DomainException("INVALID_TRANSITION", "No se puede " + action + " un pedido " + status);
        }
    }

    private Optional<OrderLine> findLine(String sku) {
        return lines.stream().filter(l -> l.sku().equals(sku)).findFirst();
    }

    private static Currency parseCurrency(String code) {
        try {
            return Currency.getInstance(code);
        } catch (RuntimeException e) {
            throw new DomainException("INVALID_CURRENCY", "Moneda desconocida: " + code);
        }
    }

    // ── Lectura (sin setters) ─────────────────────────────────────────────

    public UUID id() { return id; }
    public String customerId() { return customerId; }
    public Currency currency() { return currency; }
    public OrderStatus status() { return status; }
    public List<OrderLine> lines() { return List.copyOf(lines); }
    public Instant createdAt() { return createdAt; }
    public long version() { return version; }
}
