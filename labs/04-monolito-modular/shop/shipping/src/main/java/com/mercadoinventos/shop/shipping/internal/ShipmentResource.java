package com.mercadoinventos.shop.shipping.internal;

import io.quarkus.hibernate.orm.panache.Panache;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.util.List;

@Path("/api/shipments")
public class ShipmentResource {

    @GET
    public List<ShipmentEntity> list() {
        return Panache.getEntityManager()
                .createQuery("from ShipmentEntity order by createdAt", ShipmentEntity.class).getResultList();
    }
}
