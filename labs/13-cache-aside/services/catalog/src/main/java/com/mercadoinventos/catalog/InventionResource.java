package com.mercadoinventos.catalog;

import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/")
public class InventionResource {

    public record PriceChange(BigDecimal price) {
    }

    public record ProtectionChange(boolean enabled) {
    }

    @Inject
    InventionReader reader;

    @Inject
    InventionRepository db;

    @ConfigProperty(name = "catalog.instance", defaultValue = "catalog")
    String instance;

    /** ?cache=none | local | redis. Las cabeceras X-Cache y X-Instance dicen qué pasó y quién respondió. */
    @GET
    @Path("api/inventions/{sku}")
    public Response get(@PathParam("sku") String sku, @QueryParam("cache") @DefaultValue("redis") String cache) {
        InventionReader.Mode mode;
        try {
            mode = InventionReader.Mode.valueOf(cache.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("cache debe ser none, local o redis");
        }
        InventionReader.Read read = reader.read(sku, mode);
        Invention invention = read.invention().orElseThrow(() -> new NotFoundException("No existe " + sku));
        return Response.ok(invention).header("X-Cache", read.cache()).header("X-Instance", instance).build();
    }

    @PUT
    @Path("api/inventions/{sku}/price")
    public Map<String, Object> changePrice(@PathParam("sku") String sku, PriceChange change) {
        if (change == null || change.price() == null || change.price().signum() <= 0) {
            throw new BadRequestException("price debe ser mayor que 0");
        }
        if (!reader.updatePrice(sku, change.price())) {
            throw new NotFoundException("No existe " + sku);
        }
        return Map.of("sku", sku, "price", change.price(), "invalidatedBy", instance);
    }

    @GET
    @Path("api/stats")
    public Map<String, Object> stats() {
        return Map.of("instance", instance, "dbQueries", db.queries(),
                      "stampedeProtection", reader.stampedeProtection());
    }

    /** Simula que la clave venció (para el experimento del stampede). */
    @DELETE
    @Path("admin/cache/{sku}")
    public Response evict(@PathParam("sku") String sku) {
        reader.evict(sku);
        return Response.noContent().build();
    }

    @PUT
    @Path("admin/stampede-protection")
    public Map<String, Object> protection(ProtectionChange change) {
        reader.setStampedeProtection(change.enabled());
        return Map.of("stampedeProtection", change.enabled());
    }

    @PUT
    @Path("admin/stats/reset")
    public Map<String, Object> resetStats() {
        db.resetQueries();
        return stats();
    }
}
