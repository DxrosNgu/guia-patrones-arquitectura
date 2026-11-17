package com.mercadoinventos.catalog;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Cache-Aside: la APLICACIÓN maneja la caché (la base no sabe que existe).
 *   Leer:     buscar en la caché → si está (HIT), listo → si no (MISS), leer la base y guardarla en la caché.
 *   Escribir: actualizar la base → BORRAR la entrada de la caché (no actualizarla).
 */
@ApplicationScoped
public class InventionReader {

    private static final Logger LOG = Logger.getLogger(InventionReader.class);

    public enum Mode { NONE, LOCAL, REDIS }

    public record Read(Optional<Invention> invention, String cache) {
    }

    @Inject
    InventionRepository db;

    @Inject
    LocalCache local;

    @Inject
    RedisCache redis;

    // Ojo: el estado se lee y escribe con MÉTODOS. Este bean se inyecta como un proxy de CDI,
    // y leer un campo a través del proxy devuelve el del proxy, no el del bean real.
    private final AtomicBoolean stampedeProtection;

    InventionReader(@ConfigProperty(name = "catalog.stampede-protection") boolean stampedeProtection) {
        this.stampedeProtection = new AtomicBoolean(stampedeProtection);
    }

    public Read read(String sku, Mode mode) {
        return switch (mode) {
            case NONE -> new Read(db.find(sku), "BYPASS");
            case LOCAL -> aside(sku, local::get, local::put, () -> db.find(sku));
            case REDIS -> readThroughRedis(sku);
        };
    }

    private Read aside(String sku, java.util.function.Function<String, Optional<Invention>> cacheGet,
                       java.util.function.Consumer<Invention> cachePut, Supplier<Optional<Invention>> load) {
        Optional<Invention> cached = cacheGet.apply(sku);
        if (cached.isPresent()) {
            return new Read(cached, "HIT");
        }
        Optional<Invention> fresh = load.get();
        fresh.ifPresent(cachePut);            // lo que no existe no se cachea (ver README: caché negativa)
        return new Read(fresh, "MISS");
    }

    private Read readThroughRedis(String sku) {
        if (!stampedeProtection.get()) {
            return aside(sku, redis::get, redis::put, () -> db.find(sku));
        }
        Optional<Invention> cached = redis.get(sku);
        if (cached.isPresent()) {
            return new Read(cached, "HIT");
        }
        // Stampede: la clave no está. Solo QUIEN consigue el candado va a la base;
        // los demás esperan a que la caché se llene, en lugar de ir todos a la base a la vez.
        if (redis.tryLock(sku)) {
            try {
                Optional<Invention> fresh = db.find(sku);
                fresh.ifPresent(redis::put);
                return new Read(fresh, "MISS");
            } finally {
                redis.unlock(sku);
            }
        }
        for (int i = 0; i < 40; i++) {
            sleep(50);
            cached = redis.get(sku);
            if (cached.isPresent()) {
                return new Read(cached, "HIT-AFTER-WAIT");
            }
        }
        LOG.warnf("Nadie llenó la caché de %s en 2 s: voy a la base", sku);
        return new Read(db.find(sku), "MISS");
    }

    public boolean updatePrice(String sku, BigDecimal price) {
        boolean updated = db.updatePrice(sku, price);
        if (updated) {
            // Se BORRA, no se actualiza: la próxima lectura trae el dato fresco de la base.
            // ⚠️ Solo borra la caché local de ESTA instancia. Las otras instancias siguen con el viejo.
            local.invalidate(sku);
            redis.invalidate(sku);
        }
        return updated;
    }

    public void evict(String sku) {
        local.invalidate(sku);
        redis.invalidate(sku);
    }

    public boolean stampedeProtection() {
        return stampedeProtection.get();
    }

    public void setStampedeProtection(boolean enabled) {
        stampedeProtection.set(enabled);
    }

    void resetForTests() {
        db.resetQueries();
        local.clear();
        redis.clear();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
