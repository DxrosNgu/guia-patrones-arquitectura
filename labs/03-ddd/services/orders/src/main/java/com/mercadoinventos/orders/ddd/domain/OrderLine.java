package com.mercadoinventos.orders.ddd.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;

/**
 * Una línea del pedido. Vive DENTRO del agregado: nadie la modifica sin pasar por {@link Order}.
 * Se identifica por el SKU dentro de su pedido.
 */
@Embeddable
public record OrderLine(
        String sku,
        @AttributeOverride(name = "amount", column = @Column(name = "unit_price"))
        @AttributeOverride(name = "currency", column = @Column(name = "currency"))
        Money unitPrice,
        @Convert(converter = QuantityConverter.class)
        Quantity quantity) {

    public Money subtotal() {
        return unitPrice.times(quantity);
    }

    OrderLine withMore(Quantity extra) {
        return new OrderLine(sku, unitPrice, quantity.plus(extra));
    }
}
