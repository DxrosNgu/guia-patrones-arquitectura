package com.mercadoinventos.orders;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/** Los datos del catálogo que pedidos necesita para calcular el total (fijos, para simplificar el lab). */
public final class Catalog {

    public record Product(String name, BigDecimal price) {
    }

    private static final Map<String, Product> PRODUCTS = Map.of(
        "INV-001", new Product("Paraguas solar", new BigDecimal("49.90")),
        "INV-002", new Product("Lámpara de agua salada", new BigDecimal("89.00")),
        "INV-003", new Product("Mochila con ventilador", new BigDecimal("120.00")),
        "INV-004", new Product("Taza que avisa cuando se enfría", new BigDecimal("25.50")));

    private Catalog() {
    }

    public static Optional<Product> find(String sku) {
        return Optional.ofNullable(PRODUCTS.get(sku));
    }
}
