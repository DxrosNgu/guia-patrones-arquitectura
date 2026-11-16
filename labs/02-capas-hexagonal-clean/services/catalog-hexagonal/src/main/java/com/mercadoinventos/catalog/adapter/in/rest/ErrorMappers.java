package com.mercadoinventos.catalog.adapter.in.rest;

import com.mercadoinventos.catalog.application.InventionNotFound;
import com.mercadoinventos.catalog.domain.BusinessRuleViolation;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/** Traduce las excepciones del dominio a HTTP. Esta decisión es del adaptador REST, no del dominio. */
public class ErrorMappers {

    public record ErrorResponse(String code, String message) {
    }

    @ServerExceptionMapper
    public Response businessRule(BusinessRuleViolation e) {
        int status = switch (e.rule()) {
            case NAME_ALREADY_USED, TOO_MANY_DRAFTS, ALREADY_PUBLISHED -> 409;
            case PRICE_MUST_BE_POSITIVE, INVENTOR_REQUIRED, NAME_REQUIRED, IMAGE_REQUIRED -> 422;
        };
        return Response.status(status).entity(new ErrorResponse(e.rule().name(), e.getMessage())).build();
    }

    @ServerExceptionMapper
    public Response notFound(InventionNotFound e) {
        return Response.status(404).entity(new ErrorResponse("NOT_FOUND", e.getMessage())).build();
    }
}
