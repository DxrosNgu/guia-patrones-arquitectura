package com.mercadoinventos.orders.ddd.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void addsWithoutLosingCents() {
        // Con double: 0.1 + 0.2 = 0.30000000000000004
        Money sum = Money.of("0.10", "PEN").plus(Money.of("0.20", "PEN"));

        assertThat(sum).isEqualTo(Money.of("0.30", "PEN"));
    }

    @Test
    void equalityIsByValue() {
        assertThat(Money.of("10", "PEN")).isEqualTo(Money.of("10.00", "PEN"));
        assertThat(Money.of("10", "PEN")).isNotEqualTo(Money.of("10", "USD"));
    }

    @Test
    void cannotMixCurrencies() {
        assertThatThrownBy(() -> Money.of("10", "PEN").plus(Money.of("10", "USD")))
                .isInstanceOf(DomainException.class)
                .extracting("code").isEqualTo("CURRENCY_MISMATCH");
    }

    @Test
    void multipliesByAQuantity() {
        assertThat(Money.of("19.99", "PEN").times(new Quantity(3))).isEqualTo(Money.of("59.97", "PEN"));
    }

    @Test
    void cannotBeNegative() {
        assertThatThrownBy(() -> new Money(new BigDecimal("-1"), java.util.Currency.getInstance("PEN")))
                .extracting("code").isEqualTo("NEGATIVE_AMOUNT");
    }
}
