package com.mercadoinventos.orders.ddd.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Las invariantes del agregado Pedido, probadas sin base de datos. */
class OrderTest {

    private static Order newOrder() {
        return Order.create("ana", "PEN");
    }

    @Test
    void aNewOrderIsAnEmptyDraft() {
        Order order = newOrder();

        assertThat(order.status()).isEqualTo(OrderStatus.DRAFT);
        assertThat(order.total()).isEqualTo(Money.of("0", "PEN"));
    }

    @Test
    void totalIsAlwaysTheSumOfTheLines() {
        Order order = newOrder();
        order.addLine("INV-001", Money.of("19.99", "PEN"), new Quantity(2));
        order.addLine("INV-002", Money.of("5.00", "PEN"), new Quantity(1));
        assertThat(order.total()).isEqualTo(Money.of("44.98", "PEN"));

        order.removeLine("INV-001");
        assertThat(order.total()).isEqualTo(Money.of("5.00", "PEN"));
    }

    @Test
    void theSameInventionTwiceIsOneLine() {
        Order order = newOrder();
        order.addLine("INV-001", Money.of("10", "PEN"), new Quantity(4));
        order.addLine("INV-001", Money.of("10", "PEN"), new Quantity(3));

        assertThat(order.lines()).hasSize(1);
        assertThat(order.lines().getFirst().quantity()).isEqualTo(new Quantity(7));
    }

    @Test
    void atMostTenUnitsPerInvention() {
        Order order = newOrder();
        order.addLine("INV-001", Money.of("10", "PEN"), new Quantity(8));

        assertThatThrownBy(() -> order.addLine("INV-001", Money.of("10", "PEN"), new Quantity(3)))
                .extracting("code").isEqualTo("MAX_QUANTITY_EXCEEDED");
        assertThat(order.lines().getFirst().quantity()).isEqualTo(new Quantity(8));
    }

    @Test
    void linesMustUseTheOrderCurrency() {
        assertThatThrownBy(() -> newOrder().addLine("INV-001", Money.of("10", "USD"), new Quantity(1)))
                .extracting("code").isEqualTo("CURRENCY_MISMATCH");
    }

    @Test
    void cannotConfirmAnEmptyOrder() {
        assertThatThrownBy(() -> newOrder().confirm()).extracting("code").isEqualTo("EMPTY_ORDER");
    }

    @Test
    void confirmingRecordsADomainEvent() {
        Order order = newOrder();
        order.addLine("INV-001", Money.of("10", "PEN"), new Quantity(2));

        order.confirm();

        assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.pullDomainEvents())
                .singleElement()
                .isInstanceOfSatisfying(OrderConfirmed.class, e -> {
                    assertThat(e.orderId()).isEqualTo(order.id());
                    assertThat(e.total()).isEqualTo(Money.of("20", "PEN"));
                });
        assertThat(order.pullDomainEvents()).isEmpty();
    }

    @Test
    void cannotBeModifiedOnceConfirmed() {
        Order order = newOrder();
        order.addLine("INV-001", Money.of("10", "PEN"), new Quantity(1));
        order.confirm();

        assertThatThrownBy(() -> order.addLine("INV-002", Money.of("1", "PEN"), new Quantity(1)))
                .extracting("code").isEqualTo("ORDER_NOT_EDITABLE");
        assertThatThrownBy(() -> order.removeLine("INV-001"))
                .extracting("code").isEqualTo("ORDER_NOT_EDITABLE");
    }

    @Test
    void followsItsLifecycle() {
        Order order = newOrder();
        order.addLine("INV-001", Money.of("10", "PEN"), new Quantity(1));

        assertThatThrownBy(order::pay).extracting("code").isEqualTo("INVALID_TRANSITION");
        order.confirm();
        order.pay();
        assertThatThrownBy(order::cancel).extracting("code").isEqualTo("INVALID_TRANSITION");
        order.ship();

        assertThat(order.status()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void canBeCancelledBeforePayment() {
        Order order = newOrder();
        order.addLine("INV-001", Money.of("10", "PEN"), new Quantity(1));
        order.confirm();

        order.cancel();

        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
    }
}
