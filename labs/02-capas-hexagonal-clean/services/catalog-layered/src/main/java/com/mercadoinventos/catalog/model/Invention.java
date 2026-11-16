package com.mercadoinventos.catalog.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * En la arquitectura en capas típica, el "modelo" ES la entidad de JPA: la misma clase
 * es fila de la tabla, objeto de negocio y JSON de la respuesta. Cómodo al principio;
 * el problema es que el negocio queda atado a la base de datos y a la API a la vez.
 */
@Entity
@Table(name = "inventions")
public class Invention {

    public enum Status { DRAFT, PUBLISHED }

    @Id
    public UUID id;
    public String inventorId;
    public String name;
    public BigDecimal price;
    public String imageUrl;
    @Enumerated(EnumType.STRING)
    public Status status;
    public Instant createdAt;
}
