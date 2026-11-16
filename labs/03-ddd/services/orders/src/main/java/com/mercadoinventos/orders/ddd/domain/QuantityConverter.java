package com.mercadoinventos.orders.ddd.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Guarda la Quantity como un entero, y al leerla vuelve a pasar por sus validaciones. */
@Converter
public class QuantityConverter implements AttributeConverter<Quantity, Integer> {

    @Override
    public Integer convertToDatabaseColumn(Quantity quantity) {
        return quantity == null ? null : quantity.value();
    }

    @Override
    public Quantity convertToEntityAttribute(Integer value) {
        return value == null ? null : new Quantity(value);
    }
}
