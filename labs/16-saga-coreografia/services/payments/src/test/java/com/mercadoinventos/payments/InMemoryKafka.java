package com.mercadoinventos.payments;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;

import java.util.HashMap;
import java.util.Map;

public class InMemoryKafka implements QuarkusTestResourceLifecycleManager {

    @Override
    public Map<String, String> start() {
        Map<String, String> config = new HashMap<>();
        config.putAll(InMemoryConnector.switchIncomingChannelsToInMemory("saga-in"));
        config.putAll(InMemoryConnector.switchOutgoingChannelsToInMemory("saga-out"));
        return config;
    }

    @Override
    public void stop() {
        InMemoryConnector.clear();
    }
}
