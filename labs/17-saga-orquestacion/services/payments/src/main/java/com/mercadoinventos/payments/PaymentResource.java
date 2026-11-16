package com.mercadoinventos.payments;

import io.quarkus.panache.common.Sort;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

import java.util.List;

@Path("/api/payments")
public class PaymentResource {

    @GET
    public List<Payment> list(@QueryParam("orderId") String orderId) {
        return orderId == null
            ? Payment.listAll(Sort.descending("createdAt"))
            : Payment.list("orderId", orderId);
    }
}
