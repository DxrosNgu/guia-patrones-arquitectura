package com.mercadoinventos.orders;

import io.quarkus.panache.common.Sort;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.List;

@Path("/api")
public class OrderResource {

    public record ItemRequest(@NotBlank String sku, @Positive int quantity) {
    }

    public record OrderRequest(@NotBlank String customerId, @NotEmpty List<@Valid ItemRequest> items) {
    }

    public record OutboxRow(String id, String type, String aggregateId, Instant createdAt, Instant publishedAt) {
    }

    private final OrderService service;

    OrderResource(OrderService service) {
        this.service = service;
    }

    @POST
    @Path("/orders")
    public Response place(@Valid OrderRequest request) {
        Order order = service.place(request.customerId(),
            request.items().stream().map(i -> new Order.Item(i.sku(), i.quantity())).toList());
        return Response.accepted(order).build();
    }

    @GET
    @Path("/orders")
    public List<Order> orders() {
        return Order.listAll(Sort.descending("createdAt"));
    }

    /** Para mirar la outbox: qué eventos hay y cuáles ya se publicaron. */
    @GET
    @Path("/outbox")
    public List<OutboxRow> outbox() {
        List<OutboxEvent> rows = OutboxEvent.listAll(Sort.descending("createdAt"));
        return rows.stream()
            .map(e -> new OutboxRow(e.id.toString(), e.type, e.aggregateId, e.createdAt, e.publishedAt))
            .toList();
    }
}
