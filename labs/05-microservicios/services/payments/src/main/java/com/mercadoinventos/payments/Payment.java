package com.mercadoinventos.payments;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment extends PanacheEntityBase {

    public enum Status { APPROVED, DECLINED }

    @Id
    public UUID id;
    public String orderId;
    public String customerId;
    public BigDecimal amount;
    public String currency;
    @Enumerated(EnumType.STRING)
    public Status status;
    public String reason;
    public Instant createdAt;

    public static List<Payment> ofOrder(String orderId) {
        return list("orderId", orderId);
    }
}
