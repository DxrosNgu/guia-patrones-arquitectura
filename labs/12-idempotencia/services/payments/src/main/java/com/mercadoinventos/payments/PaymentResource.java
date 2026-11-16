package com.mercadoinventos.payments;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.math.BigDecimal;
import java.util.List;

@Path("/api/payments")
public class PaymentResource {

    public record PaymentRequest(@NotBlank String orderId, @NotBlank String customerId,
                                 @NotNull @Positive BigDecimal amount, @NotBlank String currency) {
    }

    private final PaymentService payments;
    private final IdempotentPayments idempotent;

    PaymentResource(PaymentService payments, IdempotentPayments idempotent) {
        this.payments = payments;
        this.idempotent = idempotent;
    }

    @POST
    public Response charge(@HeaderParam("Idempotency-Key") String key, @Valid PaymentRequest request) {
        if (key == null || key.isBlank()) {
            // ❌ Sin clave no hay protección: cada reintento es un cobro nuevo.
            return Response.status(Response.Status.CREATED).entity(chargeWithoutKey(request)).build();
        }
        IdempotentPayments.Result result = idempotent.charge(key, request);
        Response.ResponseBuilder response = Response.status(result.status())
            .entity(result.body()).type(MediaType.APPLICATION_JSON);
        if (result.replayed()) {
            response.header("Idempotent-Replayed", "true");
        }
        return response.build();
    }

    @Transactional
    Payment chargeWithoutKey(PaymentRequest request) {
        return payments.charge(request);
    }

    @GET
    public List<Payment> ofOrder(@QueryParam("orderId") String orderId) {
        return orderId == null ? Payment.listAll() : Payment.ofOrder(orderId);
    }
}
