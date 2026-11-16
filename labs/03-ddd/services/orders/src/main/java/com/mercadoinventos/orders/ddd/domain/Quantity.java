package com.mercadoinventos.orders.ddd.domain;

/** Value object: entre 1 y 10 unidades por invento. Una cantidad inválida no puede existir. */
public record Quantity(int value) {

    public static final int MAX_PER_INVENTION = 10;

    public Quantity {
        if (value < 1) {
            throw new DomainException("INVALID_QUANTITY", "La cantidad mínima es 1");
        }
        if (value > MAX_PER_INVENTION) {
            throw new DomainException("MAX_QUANTITY_EXCEEDED",
                    "Máximo " + MAX_PER_INVENTION + " unidades por invento (pediste " + value + ")");
        }
    }

    public Quantity plus(Quantity other) {
        return new Quantity(value + other.value);
    }
}
