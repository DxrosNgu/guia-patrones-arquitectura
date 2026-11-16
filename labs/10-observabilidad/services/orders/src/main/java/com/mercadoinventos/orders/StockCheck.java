package com.mercadoinventos.orders;

import com.mercadoinventos.orders.clients.InventoryClient;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import java.util.List;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

/**
 * Instrumentación A MANO, forma 1: una anotación.
 * {@code @WithSpan} crea un span hijo alrededor del método. Las llamadas HTTP a inventario
 * (instrumentadas automáticamente) quedan DENTRO de él, agrupadas. Si el método lanza una
 * excepción, el span queda en ERROR con la excepción registrada, sin código extra.
 */
@ApplicationScoped
public class StockCheck {

    private static final Logger LOG = Logger.getLogger(StockCheck.class);

    private final InventoryClient inventory;

    StockCheck(@RestClient InventoryClient inventory) {
        this.inventory = inventory;
    }

    @WithSpan("orders.check-stock")
    public void ensureAvailable(@SpanAttribute("order.items") int itemCount, List<Order.Item> items) {
        for (Order.Item item : items) {
            int available = availableStock(item.sku);
            if (available < item.quantity) {
                LOG.infof("Pedido rechazado: %s pide %d y hay %d", item.sku, item.quantity, available);
                throw new OrderService.OutOfStock(item.sku, available);
            }
        }
    }

    private int availableStock(String sku) {
        try {
            return inventory.stock(sku).available();
        } catch (WebApplicationException e) {
            return 0; // el SKU no existe → no hay stock
        } catch (ProcessingException e) {
            LOG.warnf("Inventario no responde al consultar %s", sku);
            throw new OrderService.InventoryUnavailable(e);
        }
    }
}
