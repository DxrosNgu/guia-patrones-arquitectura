package com.mercadoinventos.orders.ddd.domain;

import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/**
 * Value object: monto + moneda. Inmutable, se compara por valor y NUNCA usa double.
 * Sumar soles con dólares es un error de dominio, no un número raro en un reporte.
 */
@Embeddable
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        if (amount == null || currency == null) {
            throw new DomainException("INVALID_MONEY", "Monto y moneda son obligatorios");
        }
        if (amount.signum() < 0) {
            throw new DomainException("NEGATIVE_AMOUNT", "Un monto no puede ser negativo");
        }
        amount = amount.setScale(currency.getDefaultFractionDigits(), RoundingMode.HALF_UP);
    }

    public static Money of(String amount, String currency) {
        return new Money(new BigDecimal(amount), Currency.getInstance(currency));
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money times(Quantity quantity) {
        return new Money(amount.multiply(BigDecimal.valueOf(quantity.value())), currency);
    }

    public void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new DomainException("CURRENCY_MISMATCH",
                    "No se pueden mezclar " + currency.getCurrencyCode() + " y " + other.currency.getCurrencyCode());
        }
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }
}
