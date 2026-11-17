package com.mercadoinventos.orders;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

/**
 * Pedidos consulta el stock de inventario de dos formas, para compararlas:
 *   /api/discovery/dns/...     → por el nombre DNS "inventory"
 *   /api/discovery/consul/...  → con Stork + Consul
 */
@Path("/api/discovery/{via}")
public class DiscoveryResource {

    private static final Logger LOG = Logger.getLogger(DiscoveryResource.class);

    public record StockAnswer(String via, String sku, int available, String instance) {
    }

    public record Spread(String via, int calls, Map<String, Integer> byInstance, int errors) {
    }

    private final InventoryViaDns dns;
    private final InventoryViaConsul consul;

    DiscoveryResource(@RestClient InventoryViaDns dns, @RestClient InventoryViaConsul consul) {
        this.dns = dns;
        this.consul = consul;
    }

    @GET
    @Path("stock/{sku}")
    public StockAnswer stock(@PathParam("via") String via, @PathParam("sku") String sku) {
        StockLevel level = client(via).apply(sku);
        return new StockAnswer(via, level.sku(), level.available(), level.instance());
    }

    /** Hace N llamadas seguidas y cuenta qué instancia respondió cada una. */
    @GET
    @Path("spread")
    public Spread spread(@PathParam("via") String via, @QueryParam("calls") @DefaultValue("9") int calls) {
        Function<String, StockLevel> client = client(via);
        Map<String, Integer> byInstance = new TreeMap<>();
        int errors = 0;
        for (int i = 0; i < calls; i++) {
            try {
                byInstance.merge(client.apply("INV-001").instance(), 1, Integer::sum);
            } catch (RuntimeException e) {
                errors++;
                LOG.warnf("[%s] llamada %d falló: %s", via, i + 1, e.getMessage());
            }
        }
        return new Spread(via, calls, byInstance, errors);
    }

    private Function<String, StockLevel> client(String via) {
        return switch (via) {
            case "dns" -> dns::stock;
            case "consul" -> consul::stock;
            default -> throw new NotFoundException("Usa 'dns' o 'consul'");
        };
    }
}
