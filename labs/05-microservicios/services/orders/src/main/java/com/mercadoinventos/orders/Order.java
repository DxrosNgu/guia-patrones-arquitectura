package com.mercadoinventos.orders;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order extends PanacheEntityBase {

    public enum Status { CONFIRMED, REJECTED }

    @Id
    public UUID id;
    public String customerId;
    @Enumerated(EnumType.STRING)
    public Status status;
    public BigDecimal total;
    public String currency;
    public String rejectionReason;
    public Instant createdAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    public List<OrderLine> lines = new ArrayList<>();

    public void confirm() {
        status = Status.CONFIRMED;
    }

    public void reject(String reason) {
        status = Status.REJECTED;
        rejectionReason = reason;
    }
}
