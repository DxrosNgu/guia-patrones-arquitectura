package com.mercadoinventos.catalog;

import java.math.BigDecimal;

/** El CONTRATO del legado: {"sku", "name", "price"}. El servicio nuevo responde exactamente igual. */
public record ProductJson(String sku, String name, BigDecimal price) {

    static ProductJson of(Product p) {
        return new ProductJson(p.sku, p.name, p.price);
    }
}
