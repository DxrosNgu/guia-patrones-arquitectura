package com.mercadoinventos.catalog;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "products")
public class Product extends PanacheEntityBase {

    @Id
    public String sku;
    public String name;
    public BigDecimal price;
}
