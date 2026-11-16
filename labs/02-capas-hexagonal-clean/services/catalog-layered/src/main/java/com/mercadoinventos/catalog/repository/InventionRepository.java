package com.mercadoinventos.catalog.repository;

import com.mercadoinventos.catalog.model.Invention;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.UUID;

/** Capa de acceso a datos: una clase concreta de Panache. El servicio depende de ELLA, no de una abstracción. */
@ApplicationScoped
public class InventionRepository implements PanacheRepositoryBase<Invention, UUID> {

    public List<Invention> findByInventor(String inventorId) {
        return list("inventorId = ?1 order by createdAt", inventorId);
    }

    public boolean existsByInventorAndName(String inventorId, String name) {
        return count("inventorId = ?1 and lower(trim(name)) = ?2", inventorId, name.trim().toLowerCase()) > 0;
    }

    public long countDrafts(String inventorId) {
        return count("inventorId = ?1 and status = ?2", inventorId, Invention.Status.DRAFT);
    }
}
