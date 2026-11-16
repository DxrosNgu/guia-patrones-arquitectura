package com.mercadoinventos.orders.ddd.application;

import com.mercadoinventos.orders.ddd.domain.OrderConfirmed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Reacciona al evento de dominio DENTRO del mismo proceso (otro módulo, otra responsabilidad).
 * AFTER_SUCCESS: solo si la transacción hizo commit. Si falla, el correo no sale.
 * Para avisar a OTROS servicios hace falta un broker y un outbox (labs 06 y 15).
 */
@ApplicationScoped
public class OrderConfirmedListener {

    private static final Logger LOG = Logger.getLogger(OrderConfirmedListener.class);

    @Inject
    RecentEvents recentEvents;

    void onOrderConfirmed(@Observes(during = TransactionPhase.AFTER_SUCCESS) OrderConfirmed event) {
        LOG.infof("📧 Pedido %s confirmado por %s: %s en %d línea(s). Se envía el correo de confirmación.",
                event.orderId(), event.customerId(), event.total(), event.lineCount());
        recentEvents.add(event);
    }
}
