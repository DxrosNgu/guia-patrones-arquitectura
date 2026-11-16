package com.mercadoinventos.catalog.controller;

import com.mercadoinventos.catalog.model.Invention;
import com.mercadoinventos.catalog.service.BusinessException;
import com.mercadoinventos.catalog.service.InventionService;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/** Capa de presentación: recibe HTTP, llama al servicio y devuelve la ENTIDAD tal cual como JSON. */
@Path("/api/inventions")
public class InventionController {

    public record CreateInventionRequest(String inventorId, String name, BigDecimal price, String imageUrl) {
    }

    public record ChangeImageRequest(String imageUrl) {
    }

    @Inject
    InventionService service;

    @POST
    public Response create(CreateInventionRequest request) {
        if (request == null) {
            throw new BadRequestException("Falta el cuerpo de la petición");
        }
        Invention invention = service.createDraft(request.inventorId(), request.name(), request.price(), request.imageUrl());
        return Response.created(URI.create("/api/inventions/" + invention.id)).entity(invention).build();
    }

    @PUT
    @Path("{id}/image")
    public Invention changeImage(@PathParam("id") UUID id, ChangeImageRequest request) {
        return service.changeImage(id, request == null ? null : request.imageUrl());
    }

    @POST
    @Path("{id}/publish")
    public Invention publish(@PathParam("id") UUID id) {
        return service.publish(id);
    }

    @GET
    @Path("{id}")
    public Invention get(@PathParam("id") UUID id) {
        return service.get(id);
    }

    @GET
    public List<Invention> list(@QueryParam("inventorId") String inventorId) {
        if (inventorId == null || inventorId.isBlank()) {
            throw new BadRequestException("Falta el parámetro inventorId");
        }
        return service.listByInventor(inventorId);
    }

    @ServerExceptionMapper
    public Response businessError(BusinessException e) {
        return Response.status(e.httpStatus()).entity(Map.of("code", e.code(), "message", e.getMessage())).build();
    }

    @ServerExceptionMapper
    public Response notFound(NotFoundException e) {
        return Response.status(404).entity(Map.of("code", "NOT_FOUND", "message", e.getMessage())).build();
    }
}
