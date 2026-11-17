package com.mercadoinventos.orders;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

/**
 * SINCRONIZACIÓN durante la migración, en los dos sentidos, cada segundo:
 *
 * <ul>
 *   <li><b>nuevo → legado</b>: los pedidos creados aquí se copian al legado. Así, si el gateway manda un GET
 *       al legado (el 90 % del canary) lo encuentra, y si hay que hacer ROLLBACK no se pierde ningún pedido.</li>
 *   <li><b>legado → nuevo</b>: los pedidos creados en el legado se copian aquí. Así, cuando /orders pase
 *       entero al servicio nuevo, la historia de cada cliente sigue completa.</li>
 * </ul>
 */
@ApplicationScoped
public class LegacySync {

    private static final Logger LOG = Logger.getLogger(LegacySync.class);
    /**
     * Se vuelve a pedir una ventana de ids ya vistos: en el legado, el pedido 1010 puede confirmar después
     * que el 1011 (ver lab 18, "un evento que confirma tarde"). Copiar es idempotente, así que repetir no daña.
     */
    private static final int SAFETY_WINDOW = 50;

    @Inject
    @RestClient
    Clients.LegacyOrders legacy;

    private volatile boolean enabled;
    private volatile String lastError;
    private volatile Instant lastRun;

    LegacySync(@ConfigProperty(name = "orders.sync.enabled") boolean enabled) {
        this.enabled = enabled;
    }

    @Scheduled(every = "${orders.sync.every}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void tick() {
        if (!enabled) {
            return;
        }
        try {
            int pushed = pushNewOrders();
            int pulled = pullLegacyOrders();
            if (pushed + pulled > 0) {
                LOG.infof("Sincronización: %d pedidos nuevos → legado, %d pedidos del legado → aquí", pushed, pulled);
            }
            lastError = null;
        } catch (RuntimeException e) {
            if (lastError == null) {
                LOG.warnf("El legado no responde; se reintenta cada segundo (%s)", e.getMessage());
            }
            lastError = e.getMessage();
        }
        lastRun = Instant.now();
    }

    int pushNewOrders() {
        List<ShopOrder> pending = QuarkusTransaction.requiringNew().call(() ->
            ShopOrder.<ShopOrder>find("origin = ?1 and syncedToLegacy = false order by id", ShopOrder.NEW).page(0, 100).list());
        for (ShopOrder order : pending) {
            legacy.store(order.id, OrderJson.of(order));   // idempotente del lado del legado
            QuarkusTransaction.requiringNew().run(() ->
                ShopOrder.update("syncedToLegacy = true where id = ?1", order.id));
        }
        return pending.size();
    }

    int pullLegacyOrders() {
        long cursor = QuarkusTransaction.requiringNew().call(() -> ((Number) ShopOrder.getEntityManager()
            .createNativeQuery("SELECT last_id FROM sync_cursor WHERE name = 'legacy-orders'").getSingleResult()).longValue());
        List<OrderJson> orders = legacy.createdAfter(Math.max(0, cursor - SAFETY_WINDOW), 500);
        return QuarkusTransaction.requiringNew().call(() -> {
            int copied = 0;
            long max = cursor;
            for (OrderJson order : orders) {
                if (ShopOrder.findById(order.id()) == null) {
                    order.toLegacyCopy().persist();
                    copied++;
                }
                max = Math.max(max, order.id());
            }
            ShopOrder.getEntityManager().createNativeQuery("UPDATE sync_cursor SET last_id = ?1 WHERE name = 'legacy-orders'")
                .setParameter(1, max).executeUpdate();
            return copied;
        });
    }

    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", enabled);
        status.put("pendingToLegacy", QuarkusTransaction.requiringNew().call(() ->
            ShopOrder.count("origin = ?1 and syncedToLegacy = false", ShopOrder.NEW)));
        status.put("copiedFromLegacy", QuarkusTransaction.requiringNew().call(() -> ShopOrder.count("origin", ShopOrder.LEGACY)));
        status.put("lastError", lastError);
        status.put("lastRun", lastRun == null ? null : lastRun.toString());
        return status;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        LOG.infof("Sincronización con el legado: %s", enabled ? "ENCENDIDA" : "APAGADA");
    }
}
