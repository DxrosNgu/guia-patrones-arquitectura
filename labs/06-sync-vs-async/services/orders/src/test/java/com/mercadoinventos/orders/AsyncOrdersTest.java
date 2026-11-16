package com.mercadoinventos.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

/** Modo asíncrono (por defecto): aceptar el pedido y publicar un evento. */
@QuarkusTest
@WithTestResource(InMemoryKafka.class)
class AsyncOrdersTest {

    static final Map<String, Object> ORDER = Map.of(
        "customerId", "ana",
        "items", List.of(Map.of("sku", "INV-001", "quantity", 2)));

    @Inject
    @Any
    InMemoryConnector connector;

    final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void clearSink() {
        connector.sink("orders-events").clear();
    }

    @Test
    void acceptsTheOrderWithoutWaitingForInventory() {
        given().contentType(ContentType.JSON).body(ORDER)
            .when().post("/api/orders")
            .then()
            .statusCode(202)
            .body("status", equalTo("PLACED"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishesOrderPlacedKeyedByOrderIdWithTheItems() throws Exception {
        String id = given().contentType(ContentType.JSON).body(ORDER)
            .post("/api/orders").then().statusCode(202).extract().path("id");

        InMemorySink<Record<String, String>> sink = connector.sink("orders-events");
        assertThat(sink.received()).hasSize(1);
        Record<String, String> record = sink.received().getFirst().getPayload();
        JsonNode event = json.readTree(record.value());

        assertThat(record.key()).isEqualTo(id);
        assertThat(event.get("type").asText()).isEqualTo("OrderPlaced");
        assertThat(event.get("aggregateId").asText()).isEqualTo(id);
        assertThat(event.get("eventId").asText()).isNotBlank();
        assertThat(event.at("/payload/items/0/sku").asText()).isEqualTo("INV-001");
        assertThat(event.at("/payload/items/0/quantity").asInt()).isEqualTo(2);
    }

    @Test
    void exposesTheOrderSoConsumersCanAskForIt() {
        String id = given().contentType(ContentType.JSON).body(ORDER)
            .post("/api/orders").then().extract().path("id");

        given().when().get("/api/orders/" + id)
            .then()
            .statusCode(200)
            .body("items[0].sku", equalTo("INV-001"));
    }
}
