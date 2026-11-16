package com.mercadoinventos.catalog;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "inventions")
public class Invention extends PanacheEntityBase {

    @Id
    public Long id;
    public String sku;
    public String name;
    public String inventor;
    public BigDecimal price;
    public String currency;
    public boolean featured;
}
