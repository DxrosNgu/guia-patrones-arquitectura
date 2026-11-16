package com.mercadoinventos.catalog.config;

import com.mercadoinventos.catalog.adapter.out.memory.InMemoryInventionRepository;
import com.mercadoinventos.catalog.adapter.out.postgres.PostgresInventionRepository;
import com.mercadoinventos.catalog.application.CatalogService;
import com.mercadoinventos.catalog.application.port.in.ManageInventions;
import com.mercadoinventos.catalog.application.port.out.InventionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Raíz de composición: el ÚNICO lugar que conoce a la vez los puertos y los adaptadores.
 * Aquí se decide, por configuración, qué adaptador de persistencia se enchufa.
 */
@ApplicationScoped
public class ApplicationConfig {

    @Produces
    @ApplicationScoped
    InventionRepository inventionRepository(@ConfigProperty(name = "catalog.persistence") String persistence,
                                            Instance<PostgresInventionRepository> postgres) {
        return switch (persistence) {
            case "postgres" -> postgres.get();
            case "memory" -> new InMemoryInventionRepository();
            default -> throw new IllegalArgumentException(
                    "CATALOG_PERSISTENCE debe ser 'postgres' o 'memory', llegó: " + persistence);
        };
    }

    @Produces
    @ApplicationScoped
    ManageInventions manageInventions(InventionRepository repository) {
        return new CatalogService(repository);
    }
}
