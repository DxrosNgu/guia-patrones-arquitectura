package com.mercadoinventos.catalog;

import io.quarkus.hibernate.orm.panache.Panache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * La base de datos. La lectura simula una consulta CARA (pg_sleep): es lo que la caché evita repetir.
 * Cuenta cuántas consultas recibe: es la métrica que importa en este lab.
 */
@ApplicationScoped
public class InventionRepository {

    @ConfigProperty(name = "catalog.db-delay-ms")
    int dbDelayMs;

    private final AtomicInteger queries = new AtomicInteger();

    public int queries() {
        return queries.get();
    }

    public void resetQueries() {
        queries.set(0);
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public Optional<Invention> find(String sku) {
        queries.incrementAndGet();
        List<Object[]> rows = Panache.getEntityManager()
            .createNativeQuery("SELECT i.sku, i.name, i.price FROM inventions i, pg_sleep(?1) WHERE i.sku = ?2")
            .setParameter(1, dbDelayMs / 1000.0).setParameter(2, sku).getResultList();
        return rows.stream().findFirst()
            .map(r -> new Invention((String) r[0], (String) r[1], (BigDecimal) r[2]));
    }

    @Transactional
    public boolean updatePrice(String sku, BigDecimal price) {
        return Panache.getEntityManager().createNativeQuery("UPDATE inventions SET price = ?1 WHERE sku = ?2")
            .setParameter(1, price).setParameter(2, sku).executeUpdate() == 1;
    }
}
