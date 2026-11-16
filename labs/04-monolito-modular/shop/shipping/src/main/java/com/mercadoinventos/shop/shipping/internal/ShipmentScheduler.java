package com.mercadoinventos.shop.shipping.internal;

import com.mercadoinventos.shop.orders.api.OrderPlaced;
import io.quarkus.hibernate.orm.panache.Panache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.transaction.Transactional;
import java.time.Instant;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Envíos reacciona a OrderPlaced. Pedidos no sabe que este módulo existe.
 * AFTER_SUCCESS + REQUIRES_NEW: corre DESPUÉS del commit del pedido, en su propia transacción.
 * Consecuencia: si esto falla, el pedido ya está confirmado y el envío NO se crea.
 * Incluso dentro de un monolito, comunicarse por eventos trae consistencia eventual.
 */
@ApplicationScoped
class ShipmentScheduler {

    private static final Logger LOG = Logger.getLogger(ShipmentScheduler.class);

    @ConfigProperty(name = "shipping.chaos-fail", defaultValue = "false")
    boolean chaosFail;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    void onOrderPlaced(@Observes(during = TransactionPhase.AFTER_SUCCESS) OrderPlaced event) {
        if (chaosFail) {
            LOG.errorf("💥 CAOS: envíos falló para el pedido %s. El pedido YA está confirmado.", event.orderId());
            throw new IllegalStateException("caos en envíos");
        }
        ShipmentEntity shipment = new ShipmentEntity();
        shipment.orderId = event.orderId();
        shipment.status = "PENDING";
        shipment.createdAt = Instant.now();
        Panache.getEntityManager().persist(shipment);
        LOG.infof("📦 Envío programado para el pedido %s", event.orderId());
    }
}
