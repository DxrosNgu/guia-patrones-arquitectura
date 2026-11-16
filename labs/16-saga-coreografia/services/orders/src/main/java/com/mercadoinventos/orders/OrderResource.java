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
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/api/orders")
public class OrderResource {

    public record ItemRequest(@NotBlank String sku, @Positive int quantity, @NotNull @Positive BigDecimal unitPrice) {
    }

    public record OrderRequest(@NotBlank String customerId, @NotEmpty List<@Valid ItemRequest> items) {
    }

    public record OrderView(UUID id, String customerId, Order.Status status, BigDecimal total, String currency,
                            List<Order.Item> items, Instant createdAt, Instant updatedAt,
                            List<TimelineEntry> timeline) {
        static OrderView of(Order order, boolean withTimeline) {
            return new OrderView(order.id, order.customerId, order.status, order.total, order.currency,
                order.items, order.createdAt, order.updatedAt, withTimeline ? TimelineEntry.of(order.id) : null);
        }
    }

    private final OrderSaga saga;

    OrderResource(OrderSaga saga) {
        this.saga = saga;
    }

    @POST
    public Response place(@Valid OrderRequest request) {
        Order order = saga.start(request.customerId(), request.items().stream()
            .map(i -> new Order.Item(i.sku(), i.quantity(), i.unitPrice())).toList());
        // 202: la saga recién empieza; el resultado final llegará después.
        return Response.accepted(OrderView.of(order, false)).build();
    }

    @GET
    public List<OrderView> list() {
        List<Order> orders = Order.find("", Sort.descending("createdAt")).page(0, 50).list();
        return orders.stream().map(o -> OrderView.of(o, false)).toList();
    }

    @GET
    @Path("/{id}")
    public OrderView get(@PathParam("id") UUID id) {
        Order order = Order.findById(id);
        if (order == null) {
            throw new NotFoundException();
        }
        return OrderView.of(order, true);
    }

    @POST
    @Path("/{id}/cancel")
    public OrderView cancel(@PathParam("id") UUID id) {
        return OrderView.of(saga.cancel(id), true);
    }

    @Provider
    public static class NotCancellableMapper implements ExceptionMapper<OrderSaga.NotCancellable> {
        @Override
        public Response toResponse(OrderSaga.NotCancellable e) {
            return Response.status(Response.Status.CONFLICT).entity(Map.of("detail", e.getMessage())).build();
        }
    }
}
