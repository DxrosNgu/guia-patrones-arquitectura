package com.mercadoinventos.catalog.adapter.out.postgres;

import com.mercadoinventos.catalog.domain.InventionStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * La fila de la tabla. Es un detalle del adaptador: el dominio nunca la ve.
 * Si mañana cambia el esquema, cambia esta clase y el mapeo, no el dominio.
 */
@Entity
@Table(name = "inventions")
public class InventionEntity {

    @Id
    public UUID id;
    public String inventorId;
    public String name;
    public BigDecimal price;
    public String imageUrl;
    @Enumerated(EnumType.STRING)
    public InventionStatus status;
    public Instant createdAt;
}
