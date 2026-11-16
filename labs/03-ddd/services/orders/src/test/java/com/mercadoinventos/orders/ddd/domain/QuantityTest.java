package com.mercadoinventos.orders.ddd.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class QuantityTest {

    @Test
    void isBetweenOneAndTen() {
        assertThat(new Quantity(1).value()).isEqualTo(1);
        assertThat(new Quantity(10).value()).isEqualTo(10);
        assertThatThrownBy(() -> new Quantity(0)).extracting("code").isEqualTo("INVALID_QUANTITY");
        assertThatThrownBy(() -> new Quantity(11)).extracting("code").isEqualTo("MAX_QUANTITY_EXCEEDED");
    }

    @Test
    void addingBeyondTheLimitFails() {
        assertThatThrownBy(() -> new Quantity(6).plus(new Quantity(5)))
                .extracting("code").isEqualTo("MAX_QUANTITY_EXCEEDED");
    }
}
