package com.mercadoinventos.orders;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;

import java.util.Map;

/** Reemplaza el canal de Kafka por uno en memoria: los tests ven lo que se publicaría. */
public class InMemoryKafka implements QuarkusTestResourceLifecycleManager {

    @Override
    public Map<String, String> start() {
        return InMemoryConnector.switchOutgoingChannelsToInMemory("orders-events");
    }

    @Override
    public void stop() {
        InMemoryConnector.clear();
    }
}
