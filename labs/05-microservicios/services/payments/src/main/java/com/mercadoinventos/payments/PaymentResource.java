package com.mercadoinventos.payments;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Path("/api/payments")
public class PaymentResource {

    private static final Logger LOG = Logger.getLogger(PaymentResource.class);

    public record PaymentRequest(@NotBlank String orderId, @NotBlank String customerId,
                                 @NotNull @Positive BigDecimal amount, @NotBlank String currency) {
    }

    private final BigDecimal approvalLimit;

    PaymentResource(@ConfigProperty(name = "payments.approval-limit") BigDecimal approvalLimit) {
        this.approvalLimit = approvalLimit;
    }

    @POST
    @Transactional
    public Response charge(@Valid PaymentRequest request) {
        Payment payment = new Payment();
        payment.id = UUID.randomUUID();
        payment.orderId = request.orderId();
        payment.customerId = request.customerId();
        payment.amount = request.amount();
        payment.currency = request.currency();
        payment.createdAt = Instant.now();
        if (request.amount().compareTo(approvalLimit) <= 0) {
            payment.status = Payment.Status.APPROVED;
        } else {
            payment.status = Payment.Status.DECLINED;
            payment.reason = "Supera el límite de la tarjeta (" + approvalLimit.setScale(2) + ")";
        }
        payment.persist();
        LOG.infof("Pago %s del pedido %s: %s", payment.id, payment.orderId, payment.status);
        return Response.status(Response.Status.CREATED).entity(payment).build();
    }

    @GET
    public List<Payment> ofOrder(@QueryParam("orderId") String orderId) {
        return orderId == null ? Payment.listAll() : Payment.ofOrder(orderId);
    }
}
