package com.mercadoinventos.shop.orders.internal;

import com.mercadoinventos.shop.inventory.api.OutOfStockException;
import com.mercadoinventos.shop.payments.api.PaymentDeclinedException;
import io.quarkus.hibernate.orm.panache.Panache;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

@Path("/api/orders")
public class OrderResource {

    public record Item(String sku, int quantity) {
    }

    public record PlaceOrderRequest(String customerId, List<Item> items) {
    }

    @Inject
    PlaceOrderService placeOrder;

    @POST
    public Response place(PlaceOrderRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new BadRequestException("El pedido necesita al menos un ítem");
        }
        Map<String, Integer> quantities = request.items().stream()
                .collect(Collectors.toMap(Item::sku, Item::quantity, Integer::sum));
        return Response.status(201).entity(placeOrder.place(request.customerId(), quantities)).build();
    }

    @GET
    @Path("{id}")
    public OrderEntity get(@PathParam("id") UUID id) {
        OrderEntity order = Panache.getEntityManager().find(OrderEntity.class, id);
        if (order == null) {
            throw new NotFoundException();
        }
        return order;
    }

    @GET
    public List<OrderEntity> list() {
        return Panache.getEntityManager()
                .createQuery("from OrderEntity order by createdAt", OrderEntity.class).getResultList();
    }

    @ServerExceptionMapper
    public Response outOfStock(OutOfStockException e) {
        return error(409, "OUT_OF_STOCK", e.getMessage());
    }

    @ServerExceptionMapper
    public Response declined(PaymentDeclinedException e) {
        return error(402, "PAYMENT_DECLINED", e.getMessage());
    }

    @ServerExceptionMapper
    public Response unknown(UnknownProductException e) {
        return error(422, "UNKNOWN_PRODUCT", e.getMessage());
    }

    private static Response error(int status, String code, String message) {
        return Response.status(status).entity(Map.of("code", code, "message", message)).build();
    }
}
