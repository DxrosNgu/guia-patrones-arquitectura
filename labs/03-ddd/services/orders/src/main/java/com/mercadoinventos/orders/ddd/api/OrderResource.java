package com.mercadoinventos.orders.ddd.api;

import com.mercadoinventos.orders.ddd.application.OrderService;
import com.mercadoinventos.orders.ddd.application.RecentEvents;
import com.mercadoinventos.orders.ddd.domain.DomainEvent;
import com.mercadoinventos.orders.ddd.domain.Order;
import com.mercadoinventos.orders.ddd.domain.OrderLine;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;

/** API de la versión DDD. Fíjate que no hay ningún "PUT /status" ni forma de enviar el total. */
@Path("/api")
public class OrderResource {

    public record CreateOrderRequest(String customerId, String currency) {
    }

    /** {@code currency} es opcional: si no viene, se usa la del pedido. */
    public record AddLineRequest(String sku, BigDecimal unitPrice, String currency, Integer quantity) {
    }

    public record LineView(String sku, BigDecimal unitPrice, int quantity, BigDecimal subtotal) {
        static LineView of(OrderLine line) {
            return new LineView(line.sku(), line.unitPrice().amount(), line.quantity().value(), line.subtotal().amount());
        }
    }

    public record OrderView(UUID id, String customerId, String status, String currency, List<LineView> lines,
                            BigDecimal total, long version) {
        static OrderView of(Order order) {
            return new OrderView(order.id(), order.customerId(), order.status().name(),
                    order.currency().getCurrencyCode(), order.lines().stream().map(LineView::of).toList(),
                    order.total().amount(), order.version());
        }
    }

    @Inject
    OrderService service;

    @Inject
    RecentEvents recentEvents;

    @POST
    @Path("orders")
    public Response create(CreateOrderRequest request) {
        if (request == null) {
            throw new BadRequestException("Falta el cuerpo");
        }
        Order order = service.create(request.customerId(), request.currency() == null ? "PEN" : request.currency());
        return Response.created(URI.create("/api/orders/" + order.id())).entity(OrderView.of(order)).build();
    }

    @POST
    @Path("orders/{id}/lines")
    public OrderView addLine(@PathParam("id") UUID id, AddLineRequest request) {
        if (request == null || request.quantity() == null) {
            throw new BadRequestException("Faltan sku, unitPrice o quantity");
        }
        Order order = request.currency() == null
                ? service.addLine(id, request.sku(), request.unitPrice(), request.quantity())
                : service.addLine(id, request.sku(), request.unitPrice(), request.currency(), request.quantity());
        return OrderView.of(order);
    }

    @DELETE
    @Path("orders/{id}/lines/{sku}")
    public OrderView removeLine(@PathParam("id") UUID id, @PathParam("sku") String sku) {
        return OrderView.of(service.removeLine(id, sku));
    }

    @POST
    @Path("orders/{id}/confirm")
    public OrderView confirm(@PathParam("id") UUID id) {
        return OrderView.of(service.confirm(id));
    }

    @POST
    @Path("orders/{id}/pay")
    public OrderView pay(@PathParam("id") UUID id) {
        return OrderView.of(service.pay(id));
    }

    @POST
    @Path("orders/{id}/ship")
    public OrderView ship(@PathParam("id") UUID id) {
        return OrderView.of(service.ship(id));
    }

    @POST
    @Path("orders/{id}/cancel")
    public OrderView cancel(@PathParam("id") UUID id) {
        return OrderView.of(service.cancel(id));
    }

    @GET
    @Path("orders/{id}")
    public OrderView get(@PathParam("id") UUID id) {
        return OrderView.of(service.get(id));
    }

    /** Los últimos eventos de dominio que se publicaron (solo para el lab). */
    @GET
    @Path("events")
    public List<DomainEvent> events() {
        return recentEvents.list();
    }
}
