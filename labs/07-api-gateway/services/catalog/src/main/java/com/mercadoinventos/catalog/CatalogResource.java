package com.mercadoinventos.catalog;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import java.math.BigDecimal;
import java.util.List;

/** Catálogo público y de solo lectura: no necesita autenticación. No todo detrás del gateway la necesita. */
@Path("/api/catalog/inventions")
public class CatalogResource {

    public record Invention(String sku, String name, BigDecimal price) {
    }

    private static final List<Invention> INVENTIONS = List.of(
        new Invention("INV-001", "Paraguas solar", new BigDecimal("49.90")),
        new Invention("INV-002", "Lámpara de agua salada", new BigDecimal("89.00")),
        new Invention("INV-003", "Mochila con ventilador", new BigDecimal("120.00")),
        new Invention("INV-004", "Taza que avisa cuando se enfría", new BigDecimal("25.50")));

    @GET
    public List<Invention> list() {
        return INVENTIONS;
    }

    @GET
    @Path("{sku}")
    public Invention get(@PathParam("sku") String sku) {
        return INVENTIONS.stream().filter(i -> i.sku().equals(sku)).findFirst()
            .orElseThrow(() -> new NotFoundException("No existe " + sku));
    }
}
