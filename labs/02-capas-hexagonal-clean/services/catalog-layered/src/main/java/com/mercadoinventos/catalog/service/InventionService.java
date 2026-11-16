package com.mercadoinventos.catalog.service;

import com.mercadoinventos.catalog.model.Invention;
import com.mercadoinventos.catalog.repository.InventionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Capa de servicio: aquí viven TODAS las reglas. Funciona, y para un CRUD es suficiente.
 * Fíjate en lo que no se puede hacer sin tocar esta clase:
 *  - probar una regla sin levantar Quarkus y Postgres (usa Panache y @Transactional),
 *  - cambiar Postgres por memoria (depende de la clase concreta InventionRepository),
 *  - reutilizar las reglas desde otra entrada (lanza NotFoundException de JAX-RS).
 */
@ApplicationScoped
public class InventionService {

    static final int MAX_DRAFTS = 3;

    @Inject
    InventionRepository repository;

    @Transactional
    public Invention createDraft(String inventorId, String name, BigDecimal price, String imageUrl) {
        if (price == null || price.signum() <= 0) {
            throw new BusinessException("PRICE_MUST_BE_POSITIVE", 422, "El precio debe ser mayor que 0");
        }
        if (inventorId == null || inventorId.isBlank()) {
            throw new BusinessException("INVENTOR_REQUIRED", 422, "Falta el inventor");
        }
        if (name == null || name.isBlank()) {
            throw new BusinessException("NAME_REQUIRED", 422, "El nombre es obligatorio");
        }
        if (repository.existsByInventorAndName(inventorId, name)) {
            throw new BusinessException("NAME_ALREADY_USED", 409, "Ya tienes un invento llamado '" + name.trim() + "'");
        }
        if (repository.countDrafts(inventorId) >= MAX_DRAFTS) {
            throw new BusinessException("TOO_MANY_DRAFTS", 409,
                    "Máximo " + MAX_DRAFTS + " borradores por inventor: publica o borra alguno");
        }
        Invention invention = new Invention();
        invention.id = UUID.randomUUID();
        invention.inventorId = inventorId;
        invention.name = name.trim();
        invention.price = price.setScale(2, RoundingMode.HALF_UP);
        invention.imageUrl = blankToNull(imageUrl);
        invention.status = Invention.Status.DRAFT;
        invention.createdAt = Instant.now();
        repository.persist(invention);
        return invention;
    }

    @Transactional
    public Invention changeImage(UUID id, String imageUrl) {
        Invention invention = get(id);
        invention.imageUrl = blankToNull(imageUrl);
        return invention;
    }

    @Transactional
    public Invention publish(UUID id) {
        Invention invention = get(id);
        if (invention.status == Invention.Status.PUBLISHED) {
            throw new BusinessException("ALREADY_PUBLISHED", 409, "El invento ya está publicado");
        }
        if (invention.imageUrl == null) {
            throw new BusinessException("IMAGE_REQUIRED", 422, "No se puede publicar un invento sin imagen");
        }
        invention.status = Invention.Status.PUBLISHED;
        return invention;
    }

    public Invention get(UUID id) {
        return repository.findByIdOptional(id).orElseThrow(() -> new NotFoundException("No existe el invento " + id));
    }

    public List<Invention> listByInventor(String inventorId) {
        return repository.findByInventor(inventorId);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
