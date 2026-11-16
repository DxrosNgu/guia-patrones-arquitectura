package com.mercadoinventos.orders;

import io.quarkus.panache.common.Sort;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/api/orders")
public class OrderResource {

    public record ItemRequest(@NotBlank String sku, @Positive int quantity) {
    }

    public record OrderRequest(@NotBlank String customerId, @NotEmpty List<@Valid ItemRequest> items) {
    }

    private final OrderService service;

    OrderResource(OrderService service) {
        this.service = service;
    }

    @POST
    public Response place(@Valid OrderRequest request) {
        Order order = service.place(request.customerId(),
            request.items().stream().map(i -> new Order.Item(i.sku(), i.quantity())).toList());
        return Response.accepted(order).build();
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

    @Provider
    public static class OutOfStockMapper implements ExceptionMapper<OrderService.OutOfStock> {
        @Override
        public Response toResponse(OrderService.OutOfStock e) {
            return Response.status(Response.Status.CONFLICT).entity(Map.of("detail", e.getMessage())).build();
        }
    }

    @Provider
    public static class InventoryUnavailableMapper implements ExceptionMapper<OrderService.InventoryUnavailable> {
        @Override
        public Response toResponse(OrderService.InventoryUnavailable e) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity(Map.of("detail", e.getMessage())).build();
        }
    }
}
