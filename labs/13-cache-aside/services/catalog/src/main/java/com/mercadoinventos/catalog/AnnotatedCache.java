package com.mercadoinventos.catalog;

import io.quarkus.cache.CacheInvalidate;
import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;

/**
 * La MISMA caché local de LocalCache + InventionReader, pero declarada con anotaciones de Quarkus
 * (quarkus-cache, sobre Caffeine). Tres anotaciones reemplazan el "buscar, si no está leer y guardar".
 *
 * Lo que hacen sin que se vea:
 *  - El TTL sale de la configuración (quarkus.cache.caffeine.inventions.expire-after-write).
 *  - Si llegan 20 lecturas de la misma clave a la vez, solo una ejecuta el método: las demás esperan
 *    su resultado. Es protección contra el stampede... pero solo DENTRO de esta instancia.
 *  - Guarda también Optional.empty(): lo que no existe queda en caché (caché negativa sin pedirla).
 */
@ApplicationScoped
public class AnnotatedCache {

    @Inject
    InventionRepository db;

    @CacheResult(cacheName = "inventions")
    public Optional<Invention> find(String sku) {
        return db.find(sku);   // solo se ejecuta en un MISS
    }

    @CacheInvalidate(cacheName = "inventions")
    public void invalidate(String sku) {
        // El cuerpo vacío es intencional: la anotación borra la entrada de esta clave.
    }

    @CacheInvalidateAll(cacheName = "inventions")
    public void clear() {
        // Ídem: borra toda la caché "inventions".
    }
}
