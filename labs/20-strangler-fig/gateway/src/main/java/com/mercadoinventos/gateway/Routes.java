package com.mercadoinventos.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Las rutas del STRANGLER FIG. Se evalúan en orden; la primera cuyo predicado se cumple, gana:
 *
 * <ol>
 *   <li>catalog-new: /api/catalog/** → catálogo nuevo, si el catálogo ya se migró.</li>
 *   <li>orders-new:  /api/orders/**  → pedidos nuevo, si esta petición cae en el porcentaje del canary.</li>
 *   <li>legacy:      /api/**         → la tienda legada: todo lo que no se migró todavía.</li>
 * </ol>
 *
 * Los predicados leen MigrationState en cada petición: cambiarlo cambia el enrutamiento al instante.
 * La cabecera X-Route dice qué ruta se usó.
 */
@Configuration
public class Routes {

    @Bean
    RouteLocator stranglerRoutes(RouteLocatorBuilder routes, MigrationState migration,
                                 @Value("${LEGACY_URL:http://localhost:8120}") String legacy,
                                 @Value("${CATALOG_URL:http://localhost:8101}") String catalog,
                                 @Value("${ORDERS_URL:http://localhost:8102}") String orders) {
        return routes.routes()
            .route("catalog-new", r -> r.order(1)
                .path("/api/catalog/**").and().predicate(exchange -> migration.catalogOnNew())
                .filters(f -> f.addResponseHeader("X-Route", "catalog-new"))
                .uri(catalog))
            .route("orders-new", r -> r.order(2)
                .path("/api/orders/**", "/api/orders").and().predicate(exchange -> migration.orderToNew())
                .filters(f -> f.addResponseHeader("X-Route", "orders-new"))
                .uri(orders))
            .route("legacy", r -> r.order(10)
                .path("/api/**").and().predicate(exchange -> migration.legacyEnabled())
                .filters(f -> f.addResponseHeader("X-Route", "legacy"))
                .uri(legacy))
            .build();
    }
}
