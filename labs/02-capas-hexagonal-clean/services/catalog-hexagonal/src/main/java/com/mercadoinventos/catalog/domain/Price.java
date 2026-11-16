package com.mercadoinventos.catalog.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Value object: un precio inválido no puede existir. La regla "precio mayor que 0"
 * vive aquí, y no en una anotación de validación que alguien puede olvidar.
 */
public record Price(BigDecimal amount) {

    public Price {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleViolation(Rule.PRICE_MUST_BE_POSITIVE, "El precio debe ser mayor que 0");
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }
}
