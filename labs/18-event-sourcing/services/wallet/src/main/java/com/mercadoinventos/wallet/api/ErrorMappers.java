package com.mercadoinventos.wallet.api;

import com.mercadoinventos.wallet.app.WalletService.WalletNotFoundException;
import com.mercadoinventos.wallet.domain.WalletException;
import com.mercadoinventos.wallet.es.ConcurrencyException;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

public class ErrorMappers {

    @ServerExceptionMapper
    public Response notFound(WalletNotFoundException e) {
        return Response.status(404).entity(Map.of("message", e.getMessage())).build();
    }

    @ServerExceptionMapper
    public Response invalid(WalletException e) {
        return Response.status(400).entity(Map.of("message", e.getMessage())).build();
    }

    /** Se agotaron los reintentos: otro comando sigue ganando la carrera. El cliente puede volver a intentar. */
    @ServerExceptionMapper
    public Response conflict(ConcurrencyException e) {
        return Response.status(409).entity(Map.of("code", "CONCURRENT_MODIFICATION", "message", e.getMessage())).build();
    }
}
