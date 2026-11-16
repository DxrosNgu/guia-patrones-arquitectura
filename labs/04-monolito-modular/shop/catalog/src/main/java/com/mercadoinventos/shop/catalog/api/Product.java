package com.mercadoinventos.shop.catalog.api;

import java.math.BigDecimal;

/** Lo que el catálogo expone de un producto. No es la entidad JPA (esa es interna). */
public record Product(String sku, String name, BigDecimal price) {
}
