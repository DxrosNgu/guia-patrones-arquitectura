package com.mercadoinventos.orders.ddd.application;

import com.mercadoinventos.orders.ddd.domain.DomainEvent;
import com.mercadoinventos.orders.ddd.domain.Money;
import com.mercadoinventos.orders.ddd.domain.Order;
import com.mercadoinventos.orders.ddd.domain.Quantity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Servicio de aplicación: carga el agregado, le pide que haga algo y lo guarda.
 * NO tiene reglas de negocio (esas están en Order). Una transacción = un agregado.
 */
@ApplicationScoped
public class OrderService {

    @Inject
    OrderRepository orders;

    @Inject
    Event<DomainEvent> domainEvents;

    @Transactional
    public Order create(String customerId, String currency) {
        Order order = Order.create(customerId, currency);
        orders.persist(order);
        return order;
    }

    @Transactional
    public Order addLine(UUID id, String sku, BigDecimal unitPrice, int quantity) {
        return change(id, order -> order.addLine(sku, new Money(unitPrice, order.currency()), new Quantity(quantity)));
    }

    @Transactional
    public Order addLine(UUID id, String sku, BigDecimal unitPrice, String currency, int quantity) {
        return change(id, order -> order.addLine(sku, new Money(unitPrice, Currency.getInstance(currency)),
                new Quantity(quantity)));
    }

    @Transactional
    public Order removeLine(UUID id, String sku) {
        return change(id, order -> order.removeLine(sku));
    }

    @Transactional
    public Order confirm(UUID id) {
        return change(id, Order::confirm);
    }

    @Transactional
    public Order pay(UUID id) {
        return change(id, Order::pay);
    }

    @Transactional
    public Order ship(UUID id) {
        return change(id, Order::ship);
    }

    @Transactional
    public Order cancel(UUID id) {
        return change(id, Order::cancel);
    }

    public Order get(UUID id) {
        return orders.findByIdOptional(id).orElseThrow(() -> new OrderNotFound(id));
    }

    private Order change(UUID id, Consumer<Order> action) {
        Order order = get(id);
        action.accept(order);
        orders.flush(); // aquí salta la concurrencia optimista (@Version) si otro cambió el pedido
        // Los eventos se publican DENTRO de la transacción; los observadores deciden si reaccionan
        // antes o después del commit (ver OrderConfirmedListener).
        order.pullDomainEvents().forEach(domainEvents::fire);
        return order;
    }
}
