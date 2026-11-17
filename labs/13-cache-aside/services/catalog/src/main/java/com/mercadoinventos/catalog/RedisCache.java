package com.mercadoinventos.catalog;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.SetArgs;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Caché DISTRIBUIDA: Redis, compartida por todas las instancias del catálogo. */
@ApplicationScoped
public class RedisCache {

    private static final String PREFIX = "invention:";

    private final RedisDataSource redis;
    private final ValueCommands<String, Invention> values;

    @ConfigProperty(name = "catalog.cache-ttl")
    Duration ttl;

    RedisCache(RedisDataSource redis) {
        this.redis = redis;
        this.values = redis.value(Invention.class);
    }

    public Optional<Invention> get(String sku) {
        return Optional.ofNullable(values.get(PREFIX + sku));
    }

    /** Con TTL: aunque nadie invalide, el dato viejo se va solo. Es la red de seguridad. */
    public void put(Invention invention) {
        values.set(PREFIX + invention.sku(), invention, new SetArgs().ex(ttl));
    }

    public void invalidate(String sku) {
        redis.key().del(PREFIX + sku);
    }

    /**
     * Candado para el stampede: SET lock NX PX 2000. Solo UNA petición lo consigue; vence solo a los 2 s
     * por si quien lo tiene se cae.
     */
    public boolean tryLock(String sku) {
        return redis.execute("SET", "lock:" + PREFIX + sku, "1", "NX", "PX", "2000") != null;
    }

    public void unlock(String sku) {
        redis.key().del("lock:" + PREFIX + sku);
    }

    void clear() {
        var keys = redis.key().keys(PREFIX + "*");
        keys.addAll(redis.key().keys("lock:" + PREFIX + "*"));
        if (!keys.isEmpty()) {
            redis.key().del(keys.toArray(String[]::new));
        }
    }
}
