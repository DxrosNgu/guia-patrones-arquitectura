package com.mercadoinventos.orders;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;

/**
 * Polling Publisher: cada `outbox.poll-interval` lee los eventos pendientes de la
 * outbox, los publica en Kafka y los marca como publicados.
 *
 * - FOR UPDATE SKIP LOCKED: si hay varias réplicas de pedidos, cada fila la toma
 *   UNA sola réplica; las demás saltan las filas bloqueadas en vez de esperar.
 * - Garantía: AL MENOS UNA VEZ. Si el proceso muere después de publicar y antes
 *   del commit, la fila sigue pendiente y se publica de nuevo → los consumidores
 *   deben ser idempotentes (lab 12).
 */
@ApplicationScoped
public class OutboxPublisher {

    private static final Logger LOG = Logger.getLogger(OutboxPublisher.class);

    private final EntityManager em;
    private final KafkaSender kafka;
    private final String mode;
    private final int batchSize;

    OutboxPublisher(EntityManager em, KafkaSender kafka,
                    @ConfigProperty(name = "outbox.mode") String mode,
                    @ConfigProperty(name = "outbox.batch-size") int batchSize) {
        this.em = em;
        this.kafka = kafka;
        this.mode = mode;
        this.batchSize = batchSize;
    }

    @Scheduled(every = "${outbox.poll-interval}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void tick() {
        publishPending();
    }

    /** @return cuántos eventos publicó */
    public int publishPending() {
        if (!"polling".equals(mode)) {
            return 0;   // naive publica directo; en cdc publica Debezium
        }
        return QuarkusTransaction.requiringNew().call(() -> {
            @SuppressWarnings("unchecked")
            List<OutboxEvent> pending = em.createNativeQuery("""
                    SELECT * FROM outbox
                    WHERE published_at IS NULL
                    ORDER BY created_at
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED""", OutboxEvent.class)
                .setParameter("limit", batchSize)
                .getResultList();

            for (OutboxEvent event : pending) {
                kafka.send(event.aggregateId, event.payload);   // si Kafka falla → rollback → se reintenta
                event.publishedAt = Instant.now();
            }
            if (!pending.isEmpty()) {
                LOG.infof("📤 Outbox: %d evento(s) publicados", pending.size());
            }
            return pending.size();
        });
    }
}
