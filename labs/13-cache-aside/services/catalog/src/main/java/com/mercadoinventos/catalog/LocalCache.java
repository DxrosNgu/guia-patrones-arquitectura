package com.mercadoinventos.catalog;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Caché LOCAL: un mapa en la memoria de ESTA instancia. Rapidísima (sin red), pero cada
 * instancia tiene la suya: invalidar aquí no invalida en las demás.
 */
@ApplicationScoped
public class LocalCache {

    record Entry(Invention value, Instant expiresAt) {
    }

    @ConfigProperty(name = "catalog.cache-ttl")
    Duration ttl;

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public Optional<Invention> get(String sku) {
        Entry entry = entries.get(sku);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    public void put(Invention invention) {
        entries.put(invention.sku(), new Entry(invention, Instant.now().plus(ttl)));
    }

    public void invalidate(String sku) {
        entries.remove(sku);
    }

    void clear() {
        entries.clear();
    }
}
