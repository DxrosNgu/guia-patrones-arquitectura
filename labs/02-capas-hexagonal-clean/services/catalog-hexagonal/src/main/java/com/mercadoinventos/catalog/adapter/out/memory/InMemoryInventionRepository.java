package com.mercadoinventos.catalog.adapter.out.memory;

import com.mercadoinventos.catalog.application.port.out.InventionRepository;
import com.mercadoinventos.catalog.domain.Invention;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Adaptador de salida en memoria. Sirve para tests y para desarrollar sin base de datos. */
public class InMemoryInventionRepository implements InventionRepository {

    private final Map<UUID, Invention> store = new ConcurrentHashMap<>();

    @Override
    public void save(Invention invention) {
        store.put(invention.id(), invention);
    }

    @Override
    public Optional<Invention> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Invention> findByInventor(String inventorId) {
        return store.values().stream()
                .filter(i -> i.inventorId().equals(inventorId))
                .sorted(Comparator.comparing(Invention::createdAt))
                .toList();
    }

    @Override
    public String name() {
        return "memory";
    }
}
