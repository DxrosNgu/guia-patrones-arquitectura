package com.mercadoinventos.orders.clients;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.math.BigDecimal;

@RegisterRestClient(configKey = "payments")
@Path("/api/payments")
public interface PaymentsClient {

    record PaymentRequest(String orderId, String customerId, BigDecimal amount, String currency) {
    }

    record PaymentResult(String id, String status, String reason) {
        public boolean approved() {
            return "APPROVED".equals(status);
        }
    }

    @POST
    PaymentResult charge(PaymentRequest request);
}
