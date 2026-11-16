package com.mercadoinventos.catalog;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mercadoinventos.catalog.service.BusinessException;
import com.mercadoinventos.catalog.service.InventionService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Para probar UNA regla de negocio (el precio) hay que arrancar Quarkus y un Postgres:
 * la regla vive en un servicio que depende de Panache. Compara con InventionTest en
 * catalog-hexagonal, que prueba lo mismo con JUnit puro.
 */
@QuarkusTest
class InventionServiceTest {

    @Inject
    InventionService service;

    @Test
    void priceMustBePositive() {
        assertThatThrownBy(() -> service.createDraft("ana", "Gratis", BigDecimal.ZERO, null))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("PRICE_MUST_BE_POSITIVE");
    }
}
