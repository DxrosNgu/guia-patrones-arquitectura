package com.mercadoinventos.payments;

import io.smallrye.faulttolerance.api.CircuitBreakerMaintenance;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Function;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

@Path("/api")
public class PaymentResource {

    public record PayRequest(String orderId, BigDecimal amount) {
    }

    @Inject
    PaymentService payments;

    @Inject
    ResilientProvider resilient;

    @Inject
    NaiveProvider naive;

    @Inject
    CircuitBreakerMaintenance circuits;

    /** ✅ Con los cinco patrones. */
    @POST
    @Path("payments")
    public Response pay(PayRequest request) {
        return respond(request, resilient::charge);
    }

    /** ❌ Sin protección: espera lo que tarde la pasarela y propaga sus errores. */
    @POST
    @Path("payments/naive")
    public Response payNaive(PayRequest request) {
        return respond(request, naive::unprotected);
    }

    /** ❌ Reintentos inmediatos: 6 llamadas por pago cuando la pasarela falla. */
    @POST
    @Path("payments/naive-retry")
    public Response payNaiveRetry(PayRequest request) {
        return respond(request, naive::retryStorm);
    }

    @GET
    @Path("payments/{orderId}")
    public Payment get(@PathParam("orderId") String orderId) {
        return payments.find(orderId).orElseThrow(() -> new NotFoundException("No hay pago para " + orderId));
    }

    @GET
    @Path("resilience")
    public Map<String, Object> resilience() {
        return Map.of("circuit", circuits.currentState(ResilientProvider.CIRCUIT).name(),
                      "pendingConfirmation", payments.pending().size());
    }

    private Response respond(PayRequest request, Function<ChargeRequest, ChargeResult> provider) {
        if (request == null || request.orderId() == null || request.amount() == null) {
            throw new BadRequestException("Faltan orderId o amount");
        }
        Payment payment = payments.pay(request.orderId(), request.amount(), provider);
        int status = "APPROVED".equals(payment.status()) ? 201 : 202;   // 202: aceptado, se confirmará después
        return Response.status(status).entity(payment).build();
    }

    @ServerExceptionMapper
    public Response declined(PaymentDeclinedException e) {
        return Response.status(402).entity(Map.of("status", "DECLINED", "message", e.getMessage())).build();
    }

    @ServerExceptionMapper({ProviderUnavailableException.class, ProcessingException.class})
    public Response providerFailed(RuntimeException e) {
        return Response.status(502).entity(Map.of("status", "FAILED", "message", String.valueOf(e.getMessage()))).build();
    }
}
