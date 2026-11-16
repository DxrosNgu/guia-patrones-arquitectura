package com.mercadoinventos.orders;

import io.quarkus.panache.common.Sort;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Path("/api/orders")
public class OrderResource {

    public record ItemRequest(@NotBlank String sku, @Positive int quantity, @NotNull @Positive BigDecimal unitPrice) {
    }

    public record OrderRequest(@NotBlank String customerId, @NotBlank String currency,
                               @NotEmpty List<@Valid ItemRequest> items) {
    }

    private final OrderService service;

    OrderResource(OrderService service) {
        this.service = service;
    }

    @POST
    public Response place(@Valid OrderRequest request) {
        Order order = service.place(request.customerId(), request.currency(),
            request.items().stream()
                .map(i -> new OrderService.Item(i.sku(), i.quantity(), i.unitPrice()))
                .toList());
        return Response.status(Response.Status.CREATED).entity(order).build();
    }

    @GET
    public List<Order> list() {
        return Order.listAll(Sort.descending("createdAt"));
    }

    @GET
    @Path("/{id}")
    public Order get(@PathParam("id") UUID id) {
        Order order = Order.findById(id);
        if (order == null) {
            throw new NotFoundException();
        }
        return order;
    }
}
