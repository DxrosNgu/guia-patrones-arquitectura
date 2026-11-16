package com.mercadoinventos.shop.inventory.internal;

import io.quarkus.hibernate.orm.panache.Panache;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.util.List;
import java.util.Map;

@Path("/api/inventory/stock")
public class InventoryResource {

    @GET
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> stock() {
        List<Object[]> rows = Panache.getEntityManager()
                .createNativeQuery("SELECT sku, available FROM inventory.stock ORDER BY sku").getResultList();
        return rows.stream().map(r -> Map.<String, Object>of("sku", r[0], "available", r[1])).toList();
    }
}
