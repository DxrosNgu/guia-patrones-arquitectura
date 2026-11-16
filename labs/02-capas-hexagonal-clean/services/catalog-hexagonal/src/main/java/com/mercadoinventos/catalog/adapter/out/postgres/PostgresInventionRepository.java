package com.mercadoinventos.catalog.adapter.out.postgres;

import com.mercadoinventos.catalog.application.port.out.InventionRepository;
import com.mercadoinventos.catalog.domain.Invention;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador de salida para Postgres (JPA). Traduce entre el dominio y la tabla.
 * {@code @Typed}: Quarkus lo registra solo con su propio tipo, así no compite con
 * el adaptador en memoria; {@code ApplicationConfig} elige cuál usar.
 */
@ApplicationScoped
@Typed(PostgresInventionRepository.class)
public class PostgresInventionRepository implements InventionRepository {

    @Inject
    EntityManager em;

    @Override
    public void save(Invention invention) {
        em.merge(toEntity(invention));
    }

    @Override
    public Optional<Invention> findById(UUID id) {
        return Optional.ofNullable(em.find(InventionEntity.class, id)).map(PostgresInventionRepository::toDomain);
    }

    @Override
    public List<Invention> findByInventor(String inventorId) {
        return em.createQuery("from InventionEntity where inventorId = :inventor order by createdAt", InventionEntity.class)
                .setParameter("inventor", inventorId)
                .getResultStream()
                .map(PostgresInventionRepository::toDomain)
                .toList();
    }

    @Override
    public String name() {
        return "postgres";
    }

    private static InventionEntity toEntity(Invention invention) {
        InventionEntity entity = new InventionEntity();
        entity.id = invention.id();
        entity.inventorId = invention.inventorId();
        entity.name = invention.name();
        entity.price = invention.price().amount();
        entity.imageUrl = invention.imageUrl();
        entity.status = invention.status();
        entity.createdAt = invention.createdAt();
        return entity;
    }

    private static Invention toDomain(InventionEntity e) {
        return Invention.restore(e.id, e.inventorId, e.name, e.price, e.imageUrl, e.status, e.createdAt);
    }
}
