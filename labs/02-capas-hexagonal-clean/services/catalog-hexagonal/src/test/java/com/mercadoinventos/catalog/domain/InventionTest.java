package com.mercadoinventos.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Tests del dominio: JUnit puro. No arrancan Quarkus ni necesitan base de datos,
 * por eso corren en milisegundos. Compáralos con los tests del servicio en capas.
 */
class InventionTest {

    @Test
    void aNewInventionIsADraft() {
        Invention invention = Invention.draft("ana", "Paraguas solar", new BigDecimal("49.9"), null);

        assertThat(invention.status()).isEqualTo(InventionStatus.DRAFT);
        assertThat(invention.price().amount()).isEqualByComparingTo("49.90");
        assertThat(invention.id()).isNotNull();
    }

    @Test
    void priceMustBePositive() {
        assertThatThrownBy(() -> new Price(BigDecimal.ZERO))
                .isInstanceOf(BusinessRuleViolation.class)
                .extracting("rule").isEqualTo(Rule.PRICE_MUST_BE_POSITIVE);
        assertThatThrownBy(() -> new Price(new BigDecimal("-1")))
                .extracting("rule").isEqualTo(Rule.PRICE_MUST_BE_POSITIVE);
    }

    @Test
    void nameIsRequired() {
        assertThatThrownBy(() -> Invention.draft("ana", "  ", BigDecimal.TEN, null))
                .extracting("rule").isEqualTo(Rule.NAME_REQUIRED);
    }

    @Test
    void cannotPublishWithoutImage() {
        Invention invention = Invention.draft("ana", "Paraguas solar", BigDecimal.TEN, null);

        assertThatThrownBy(invention::publish)
                .extracting("rule").isEqualTo(Rule.IMAGE_REQUIRED);
        assertThat(invention.status()).isEqualTo(InventionStatus.DRAFT);
    }

    @Test
    void publishesOnceItHasAnImage() {
        Invention invention = Invention.draft("ana", "Paraguas solar", BigDecimal.TEN, null);
        invention.changeImage("https://img.example/paraguas.png");

        invention.publish();

        assertThat(invention.status()).isEqualTo(InventionStatus.PUBLISHED);
    }

    @Test
    void cannotPublishTwice() {
        Invention invention = Invention.draft("ana", "Paraguas solar", BigDecimal.TEN, "https://img.example/p.png");
        invention.publish();

        assertThatThrownBy(invention::publish)
                .extracting("rule").isEqualTo(Rule.ALREADY_PUBLISHED);
    }
}
