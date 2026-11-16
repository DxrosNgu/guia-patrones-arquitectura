package com.mercadoinventos.orders;

import com.mercadoinventos.orders.clients.InventoryClient;
import com.mercadoinventos.orders.clients.PaymentsClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Crear un pedido toca TRES servicios, cada uno con su base de datos:
 *
 *   1. orders    → guarda el pedido          (base orders)
 *   2. inventory → reserva el stock           (base inventory)
 *   3. payments  → cobra                      (base payments)
 *
 * No existe una transacción que abarque las tres bases. ⚠️ Este lab NO maneja
 * los fallos parciales a propósito: si el pago falla DESPUÉS de reservar, el
 * stock queda reservado para un pedido rechazado. Ese es el problema que
 * resuelven los labs 15, 16 y 17 (Outbox y Saga).
 */
@ApplicationScoped
public class OrderService {

    private static final Logger LOG = Logger.getLogger(OrderService.class);

    public record Item(String sku, int quantity, BigDecimal unitPrice) {
    }

    private final InventoryClient inventory;
    private final PaymentsClient payments;

    OrderService(@RestClient InventoryClient inventory, @RestClient PaymentsClient payments) {
        this.inventory = inventory;
        this.payments = payments;
    }

    @Transactional
    public Order place(String customerId, String currency, List<Item> items) {
        Order order = newOrder(customerId, currency, items);

        try {
            inventory.reserve(new InventoryClient.ReservationRequest(order.id.toString(),
                items.stream().map(i -> new InventoryClient.Item(i.sku(), i.quantity())).toList()));
        } catch (WebApplicationException e) {
            order.reject("Sin stock (inventario respondió " + e.getResponse().getStatus() + ")");
            return save(order);
        } catch (ProcessingException e) {
            order.reject("El servicio de inventario no responde");
            return save(order);
        }

        try {
            PaymentsClient.PaymentResult result = payments.charge(
                new PaymentsClient.PaymentRequest(order.id.toString(), customerId, order.total, currency));
            if (result.approved()) {
                order.confirm();
            } else {
                order.reject("Pago rechazado: " + result.reason());
            }
        } catch (ProcessingException | WebApplicationException e) {
            // ⚠️ El stock YA está reservado en otra base. Aquí nadie lo devuelve.
            order.reject("El servicio de pagos no responde");
        }
        return save(order);
    }

    private static Order newOrder(String customerId, String currency, List<Item> items) {
        Order order = new Order();
        order.id = UUID.randomUUID();
        order.customerId = customerId;
        order.currency = currency;
        order.createdAt = Instant.now();
        order.total = BigDecimal.ZERO;
        for (Item item : items) {
            OrderLine line = new OrderLine();
            line.order = order;
            line.sku = item.sku();
            line.quantity = item.quantity();
            line.unitPrice = item.unitPrice();
            order.lines.add(line);
            order.total = order.total.add(item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())));
        }
        return order;
    }

    private static Order save(Order order) {
        order.persist();
        LOG.infof("Pedido %s → %s %s", order.id, order.status,
            order.rejectionReason == null ? "" : "(" + order.rejectionReason + ")");
        return order;
    }
}
