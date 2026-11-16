package com.mercadoinventos.shop.payments.internal;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "payments", name = "payments")
public class PaymentEntity {

    @Id
    public UUID id;
    public UUID orderId;
    public String customerId;
    public BigDecimal amount;
    public Instant createdAt;
}
