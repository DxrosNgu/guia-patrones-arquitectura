package com.mercadoinventos.orders;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/** Un perfil de test por modo de publicación. */
public final class OutboxModes {

    private OutboxModes() {
    }

    public static class Naive implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("outbox.mode", "naive");
        }
    }

    public static class Cdc implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("outbox.mode", "cdc");
        }
    }
}
