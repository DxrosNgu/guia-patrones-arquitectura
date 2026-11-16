package com.mercadoinventos.orders;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;

/** Versión ingenua: guardar y publicar directamente (dual write), sin outbox. */
@QuarkusTest
@WithTestResource(InMemoryKafka.class)
@TestProfile(OutboxModes.Naive.class)
class NaiveDualWriteTest {

    @Inject
    @Any
    InMemoryConnector connector;

    @Test
    void publishesDirectlyAndWritesNoOutboxRow() {
        connector.sink("orders-events").clear();

        PollingOutboxTest.placeOrder();

        assertThat(connector.sink("orders-events").received()).hasSize(1);
        given().when().get("/api/outbox").then().body("$", empty());
    }
}
