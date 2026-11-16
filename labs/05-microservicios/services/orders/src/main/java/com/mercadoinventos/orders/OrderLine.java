package com.mercadoinventos.orders;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "order_lines")
public class OrderLine extends PanacheEntity {

    @ManyToOne
    @JsonIgnore
    public Order order;
    public String sku;
    public int quantity;
    public BigDecimal unitPrice;
}
