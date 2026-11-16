package com.mercadoinventos.inventory;

import io.quarkus.hibernate.orm.panache.Panache;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import java.util.List;
import java.util.Map;

@Path("/api")
public class InventoryResource {

    @GET
    @Path("stock")
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> stock() {
        List<Object[]> rows = Panache.getEntityManager()
                .createNativeQuery("SELECT sku, available FROM stock ORDER BY sku").getResultList();
        return rows.stream().map(r -> Map.<String, Object>of("sku", r[0], "available", r[1])).toList();
    }

    @GET
    @Path("reservations")
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> reservations(@QueryParam("orderId") String orderId) {
        List<Object[]> rows = Panache.getEntityManager()
                .createNativeQuery("SELECT order_id, sku, quantity, status, COALESCE(reason, '') FROM reservations "
                        + "WHERE (?1 IS NULL OR order_id = ?1) ORDER BY id")
                .setParameter(1, orderId).getResultList();
        return rows.stream().map(r -> Map.<String, Object>of("orderId", r[0], "sku", r[1], "quantity", r[2],
                "status", r[3], "reason", r[4])).toList();
    }
}
