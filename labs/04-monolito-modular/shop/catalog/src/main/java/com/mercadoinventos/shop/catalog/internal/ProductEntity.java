package com.mercadoinventos.shop.catalog.internal;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Interna: vive en el esquema "catalog" y ningún otro módulo la conoce. */
@Entity
@Table(schema = "catalog", name = "products")
public class ProductEntity {

    @Id
    public String sku;
    public String name;
    public BigDecimal price;
}
