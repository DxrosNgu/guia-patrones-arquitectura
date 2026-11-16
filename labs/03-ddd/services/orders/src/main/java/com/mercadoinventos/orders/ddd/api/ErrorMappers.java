package com.mercadoinventos.orders.ddd.api;

import com.mercadoinventos.orders.ddd.application.OrderNotFound;
import com.mercadoinventos.orders.ddd.domain.DomainException;
import jakarta.persistence.OptimisticLockException;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.Set;
import org.hibernate.StaleObjectStateException;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

public class ErrorMappers {

    /** Errores de estado: la petición es válida, pero no en el estado actual del pedido. */
    private static final Set<String> CONFLICTS = Set.of("ORDER_NOT_EDITABLE", "INVALID_TRANSITION");

    @ServerExceptionMapper
    public Response domain(DomainException e) {
        int status = CONFLICTS.contains(e.code()) ? 409 : 422;
        return Response.status(status).entity(Map.of("code", e.code(), "message", e.getMessage())).build();
    }

    @ServerExceptionMapper
    public Response notFound(OrderNotFound e) {
        return Response.status(404).entity(Map.of("code", "NOT_FOUND", "message", e.getMessage())).build();
    }

    @ServerExceptionMapper({OptimisticLockException.class, StaleObjectStateException.class})
    public Response concurrentChange(RuntimeException e) {
        return Response.status(409).entity(Map.of("code", "CONCURRENT_MODIFICATION",
                "message", "Otro cambio modificó el pedido al mismo tiempo. Vuelve a cargarlo e intenta de nuevo.")).build();
    }
}
