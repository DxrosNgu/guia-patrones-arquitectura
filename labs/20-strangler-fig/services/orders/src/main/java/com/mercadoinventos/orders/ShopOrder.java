package com.mercadoinventos.orders;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "orders")
public class ShopOrder extends PanacheEntityBase {

    public static final String NEW = "NEW";
    public static final String LEGACY = "LEGACY";

    @Id
    public Long id;
    public String customer;
    public String sku;
    public int quantity;
    public BigDecimal total;
    public Instant createdAt;
    public String origin;
    public boolean syncedToLegacy;
}
