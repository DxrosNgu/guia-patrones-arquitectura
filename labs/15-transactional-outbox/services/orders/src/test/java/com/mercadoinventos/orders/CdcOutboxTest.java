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

/** Modo CDC: pedidos solo escribe la outbox; Debezium (fuera del servicio) publica. */
@QuarkusTest
@WithTestResource(InMemoryKafka.class)
@TestProfile(OutboxModes.Cdc.class)
class CdcOutboxTest {

    @Inject
    @Any
    InMemoryConnector connector;

    @Inject
    OutboxPublisher publisher;

    @Test
    void writesTheOutboxButNeverPublishesItself() {
        connector.sink("orders-events").clear();
        String id = PollingOutboxTest.placeOrder();

        assertThat(publisher.publishPending()).isZero();
        assertThat(connector.sink("orders-events").received()).isEmpty();
        given().when().get("/api/outbox").then()
            .body("aggregateId", org.hamcrest.Matchers.hasItem(id));
    }
}
