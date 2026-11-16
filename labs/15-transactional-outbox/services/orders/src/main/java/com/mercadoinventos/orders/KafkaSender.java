package com.mercadoinventos.orders;

import io.smallrye.reactive.messaging.kafka.Record;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import java.util.concurrent.TimeUnit;

/** Envía a orders.events y ESPERA la confirmación de Kafka (si falla, lanza excepción). */
@ApplicationScoped
public class KafkaSender {

    private final Emitter<Record<String, String>> emitter;

    KafkaSender(@Channel("orders-events") Emitter<Record<String, String>> emitter) {
        this.emitter = emitter;
    }

    public void send(String key, String value) {
        emitter.send(Record.of(key, value)).toCompletableFuture().orTimeout(10, TimeUnit.SECONDS).join();
    }
}
