package com.mercadoinventos.catalog.adapter.in.rest;

import com.mercadoinventos.catalog.application.port.in.CreateDraftCommand;
import com.mercadoinventos.catalog.application.port.in.ManageInventions;
import com.mercadoinventos.catalog.domain.Invention;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Adaptador de ENTRADA REST: traduce HTTP ⇄ casos de uso. No contiene reglas de negocio.
 * Depende del puerto {@link ManageInventions}, no de la implementación.
 */
@Path("/api/inventions")
public class InventionResource {

    public record CreateInventionRequest(String inventorId, String name, BigDecimal price, String imageUrl) {
    }

    public record ChangeImageRequest(String imageUrl) {
    }

    public record InventionResponse(UUID id, String inventorId, String name, BigDecimal price, String imageUrl,
                                    String status, Instant createdAt) {
        static InventionResponse from(Invention i) {
            return new InventionResponse(i.id(), i.inventorId(), i.name(), i.price().amount(), i.imageUrl(),
                    i.status().name(), i.createdAt());
        }
    }

    @Inject
    ManageInventions inventions;

    @POST
    @Transactional
    public Response create(CreateInventionRequest request) {
        if (request == null) {
            throw new BadRequestException("Falta el cuerpo de la petición");
        }
        Invention invention = inventions.createDraft(
                new CreateDraftCommand(request.inventorId(), request.name(), request.price(), request.imageUrl()));
        return Response.created(URI.create("/api/inventions/" + invention.id()))
                .entity(InventionResponse.from(invention)).build();
    }

    @PUT
    @Path("{id}/image")
    @Transactional
    public InventionResponse changeImage(@PathParam("id") UUID id, ChangeImageRequest request) {
        return InventionResponse.from(inventions.changeImage(id, request == null ? null : request.imageUrl()));
    }

    @POST
    @Path("{id}/publish")
    @Transactional
    public InventionResponse publish(@PathParam("id") UUID id) {
        return InventionResponse.from(inventions.publish(id));
    }

    @GET
    @Path("{id}")
    public InventionResponse get(@PathParam("id") UUID id) {
        return InventionResponse.from(inventions.get(id));
    }

    @GET
    public List<InventionResponse> list(@QueryParam("inventorId") String inventorId) {
        if (inventorId == null || inventorId.isBlank()) {
            throw new BadRequestException("Falta el parámetro inventorId");
        }
        return inventions.listByInventor(inventorId).stream().map(InventionResponse::from).toList();
    }
}
