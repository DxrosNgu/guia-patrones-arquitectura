package com.mercadoinventos.shop.shipping.internal;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "shipping", name = "shipments")
public class ShipmentEntity {

    @Id
    public UUID orderId;
    public String status;
    public Instant createdAt;
}
