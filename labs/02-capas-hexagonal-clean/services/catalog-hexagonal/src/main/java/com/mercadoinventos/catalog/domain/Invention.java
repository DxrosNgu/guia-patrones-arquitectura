package com.mercadoinventos.catalog.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * El invento del dominio: Java puro, sin anotaciones de JPA ni de Jackson.
 * Las reglas que dependen solo del invento están aquí; las que necesitan
 * ver otros inventos del inventor están en {@link DraftPolicy}.
 */
public class Invention {

    private final UUID id;
    private final String inventorId;
    private final String name;
    private final Price price;
    private String imageUrl;
    private InventionStatus status;
    private final Instant createdAt;

    private Invention(UUID id, String inventorId, String name, Price price, String imageUrl,
                      InventionStatus status, Instant createdAt) {
        this.id = id;
        this.inventorId = inventorId;
        this.name = name;
        this.price = price;
        this.imageUrl = imageUrl;
        this.status = status;
        this.createdAt = createdAt;
    }

    /** Crea un borrador nuevo. */
    public static Invention draft(String inventorId, String name, BigDecimal price, String imageUrl) {
        if (inventorId == null || inventorId.isBlank()) {
            throw new BusinessRuleViolation(Rule.INVENTOR_REQUIRED, "Falta el inventor");
        }
        if (name == null || name.isBlank()) {
            throw new BusinessRuleViolation(Rule.NAME_REQUIRED, "El nombre es obligatorio");
        }
        return new Invention(UUID.randomUUID(), inventorId, name.trim(), new Price(price), blankToNull(imageUrl),
                InventionStatus.DRAFT, Instant.now());
    }

    /** Reconstruye un invento ya guardado (lo usan los adaptadores de persistencia). */
    public static Invention restore(UUID id, String inventorId, String name, BigDecimal price, String imageUrl,
                                    InventionStatus status, Instant createdAt) {
        return new Invention(id, inventorId, name, new Price(price), imageUrl, status, createdAt);
    }

    public void changeImage(String imageUrl) {
        this.imageUrl = blankToNull(imageUrl);
    }

    public void publish() {
        if (status == InventionStatus.PUBLISHED) {
            throw new BusinessRuleViolation(Rule.ALREADY_PUBLISHED, "El invento ya está publicado");
        }
        if (imageUrl == null) {
            throw new BusinessRuleViolation(Rule.IMAGE_REQUIRED, "No se puede publicar un invento sin imagen");
        }
        status = InventionStatus.PUBLISHED;
    }

    public boolean isDraft() {
        return status == InventionStatus.DRAFT;
    }

    public boolean hasSameNameAs(String otherName) {
        return name.trim().equalsIgnoreCase(otherName.trim());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID id() { return id; }
    public String inventorId() { return inventorId; }
    public String name() { return name; }
    public Price price() { return price; }
    public String imageUrl() { return imageUrl; }
    public InventionStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
}
