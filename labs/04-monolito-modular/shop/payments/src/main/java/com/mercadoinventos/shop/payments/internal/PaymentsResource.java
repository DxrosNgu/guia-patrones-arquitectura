package com.mercadoinventos.shop.payments.internal;

import io.quarkus.hibernate.orm.panache.Panache;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.util.List;

@Path("/api/payments")
public class PaymentsResource {

    @GET
    public List<PaymentEntity> list() {
        return Panache.getEntityManager()
                .createQuery("from PaymentEntity order by createdAt", PaymentEntity.class).getResultList();
    }
}
