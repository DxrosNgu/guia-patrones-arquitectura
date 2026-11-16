package com.mercadoinventos.orders.saga;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Polling Publisher (lab 15): publica la outbox en Kafka, cada fila en su topic. */
@ApplicationScoped
public class OutboxRelay {

    private static final Logger LOG = Logger.getLogger(OutboxRelay.class);

    private final EntityManager em;
    private final Emitter<String> emitter;

    OutboxRelay(EntityManager em, @Channel("saga-out") Emitter<String> emitter) {
        this.em = em;
        this.emitter = emitter;
    }

    @Scheduled(every = "${outbox.poll-interval}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void tick() {
        publishPending();
    }

    public int publishPending() {
        return QuarkusTransaction.requiringNew().call(() -> {
            @SuppressWarnings("unchecked")
            List<Object[]> rows = em.createNativeQuery("""
                    SELECT id, topic, key, payload FROM outbox
                    WHERE published_at IS NULL ORDER BY created_at LIMIT 50
                    FOR UPDATE SKIP LOCKED""").getResultList();
            for (Object[] row : rows) {
                CompletableFuture<Void> ack = new CompletableFuture<>();
                var metadata = OutgoingKafkaRecordMetadata.<String>builder()
                    .withTopic((String) row[1]).withKey((String) row[2]).build();
                emitter.send(Message.of((String) row[3])
                    .addMetadata(metadata)
                    .withAck(() -> {
                        ack.complete(null);
                        return CompletableFuture.completedFuture(null);
                    })
                    .withNack(error -> {
                        ack.completeExceptionally(error);
                        return CompletableFuture.completedFuture(null);
                    }));
                ack.get(10, TimeUnit.SECONDS);   // si Kafka no confirma → excepción → rollback → reintento
                em.createNativeQuery("UPDATE outbox SET published_at = now() WHERE id = ?1")
                    .setParameter(1, row[0]).executeUpdate();
            }
            if (!rows.isEmpty()) {
                LOG.infof("📤 Outbox: %d evento(s) publicados", rows.size());
            }
            return rows.size();
        });
    }
}
