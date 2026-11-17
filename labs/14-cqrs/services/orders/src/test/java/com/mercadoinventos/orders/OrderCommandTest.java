package com.mercadoinventos.orders;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.reactive.messaging.kafka.Record;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** El lado de COMANDOS: valida, guarda y publica. No sabe nada de cómo se va a consultar después. */
@QuarkusTest
@WithTestResource(InMemoryKafka.class)
class OrderCommandTest {

    @Inject
    @Any
    InMemoryConnector connector;

    InMemorySink<Record<String, String>> sink;

    @BeforeEach
    void setUp() {
        sink = connector.sink("orders-events");
        sink.clear();
    }

    @Test
    void placingAnOrderSavesItAndPublishesOrderPlacedWithEverythingTheReadSideNeeds() {
        String id = given().contentType(ContentType.JSON)
            .body("{\"customerId\":\"ana\",\"items\":[{\"sku\":\"INV-001\",\"quantity\":2}]}")
            .post("/api/orders")
            .then().statusCode(201)
            .body("status", equalTo("PLACED"))
            .body("version", equalTo(1))
            .body("total", equalTo(99.8f))
            .extract().path("id");

        assertThat(sink.received()).hasSize(1);
        Record<String, String> record = sink.received().getFirst().getPayload();
        assertThat(record.key()).isEqualTo(id);   // la key es el pedido: sus eventos van en orden
        String event = record.value();
        assertThat(event).contains("\"type\":\"OrderPlaced\"", "\"aggregateId\":\"" + id + "\"", "\"version\":1",
            "\"name\":\"Paraguas solar\"", "\"total\":99.8");
    }

    @Test
    void unknownProductIsRejected() {
        given().contentType(ContentType.JSON)
            .body("{\"customerId\":\"ana\",\"items\":[{\"sku\":\"NOPE\",\"quantity\":1}]}")
            .post("/api/orders").then().statusCode(422);
        assertThat(sink.received()).isEmpty();
    }
}
