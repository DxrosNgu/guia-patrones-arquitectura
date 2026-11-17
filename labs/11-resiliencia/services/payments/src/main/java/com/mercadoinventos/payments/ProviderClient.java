package com.mercadoinventos.payments;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import io.quarkus.rest.client.reactive.ClientExceptionMapper;

/** La pasarela de pagos EXTERNA. Este cliente no tiene ninguna protección: la agrega quien lo usa. */
@RegisterRestClient(configKey = "provider")
@Path("/charges")
public interface ProviderClient {

    @POST
    ChargeResult charge(ChargeRequest request);

    /** 402 es negocio (no se reintenta); 5xx es una falla (sí se reintenta). */
    @ClientExceptionMapper
    static RuntimeException toException(Response response) {
        if (response.getStatus() == 402) {
            return new PaymentDeclinedException();
        }
        if (response.getStatus() >= 500) {
            return new ProviderUnavailableException(response.getStatus());
        }
        return null;
    }
}
