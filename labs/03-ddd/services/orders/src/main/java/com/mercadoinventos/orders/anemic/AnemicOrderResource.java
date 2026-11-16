package com.mercadoinventos.orders.anemic;

import io.quarkus.hibernate.orm.panache.Panache;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;

/**
 * ❌ VERSIÓN INGENUA. Tres endpoints escritos por tres personas distintas:
 * uno calcula el total, otro se olvida, y el tercero deja poner cualquier estado.
 * Ninguno es "incorrecto" por sí solo: el problema es que las reglas no tienen dueño.
 */
@Path("/api/anemic/orders")
@Transactional
public class AnemicOrderResource {

    public record CreateRequest(String customerId, List<AnemicOrder.Line> lines) {
    }

    public record StatusRequest(String status) {
    }

    @POST
    public Response create(CreateRequest request) {
        AnemicOrder order = new AnemicOrder();
        order.setId(UUID.randomUUID());
        order.setCustomerId(request.customerId());
        order.setStatus("DRAFT");
        order.setLines(request.lines() == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(request.lines()));
        // Este endpoint SÍ calcula el total... con double.
        double total = 0;
        for (AnemicOrder.Line line : order.getLines()) {
            total += line.getUnitPrice() * line.getQuantity();
        }
        order.setTotal(total);
        Panache.getEntityManager().persist(order);
        return Response.status(201).entity(order).build();
    }

    @POST
    @Path("{id}/lines")
    public AnemicOrder addLine(@PathParam("id") UUID id, AnemicOrder.Line line) {
        AnemicOrder order = find(id);
        // ❌ Se olvida de recalcular el total, y de comprobar el estado y la cantidad.
        order.getLines().add(line);
        return order;
    }

    @PUT
    @Path("{id}/status")
    public AnemicOrder changeStatus(@PathParam("id") UUID id, StatusRequest request) {
        AnemicOrder order = find(id);
        order.setStatus(request.status()); // ❌ de SHIPPED a DRAFT, o a "banana"
        return order;
    }

    @GET
    @Path("{id}")
    public AnemicOrder get(@PathParam("id") UUID id) {
        return find(id);
    }

    private static AnemicOrder find(UUID id) {
        AnemicOrder order = Panache.getEntityManager().find(AnemicOrder.class, id);
        if (order == null) {
            throw new NotFoundException();
        }
        return order;
    }
}
