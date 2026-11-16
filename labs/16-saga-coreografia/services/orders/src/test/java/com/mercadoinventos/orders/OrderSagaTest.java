package com.mercadoinventos.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercadoinventos.orders.saga.OutboxRelay;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
@WithTestResource(InMemoryKafka.class)
class OrderSagaTest {

    @Inject
    OrderSaga saga;

    @Inject
    OutboxRelay relay;

    @Inject
    @Any
    InMemoryConnector connector;

    final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void clean() {
        relay.publishPending();
        connector.sink("saga-out").clear();
    }

    static String placeOrder() {
        return given().contentType(ContentType.JSON)
            .body(Map.of("customerId", "ana", "items", List.of(
                Map.of("sku", "INV-001", "quantity", 2, "unitPrice", 49.90),
                Map.of("sku", "INV-003", "quantity", 1, "unitPrice", 189.00))))
            .post("/api/orders").then().statusCode(202).extract().path("id");
    }

    static String event(String type, String orderId) {
        return """
            {"eventId":"%s","type":"%s","aggregateId":"%s","occurredAt":"2026-11-16T10:00:00Z","payload":{"reason":"motivo de prueba"}}"""
            .formatted(UUID.randomUUID(), type, orderId);
    }

    void receive(String orderId, String... types) {
        for (String type : types) {
            saga.on(event(type, orderId));
        }
    }

    String status(String orderId) {
        return given().get("/api/orders/" + orderId).then().extract().path("status");
    }

    List<JsonNode> published() throws Exception {
        relay.publishPending();
        InMemorySink<String> sink = connector.sink("saga-out");
        List<JsonNode> events = new java.util.ArrayList<>();
        for (var message : sink.received()) {
            events.add(json.readTree(message.getPayload()));
        }
        return events;
    }

    @Test
    void placingAnOrderStartsTheSaga() throws Exception {
        String id = placeOrder();

        assertThat(status(id)).isEqualTo("PENDING");
        JsonNode created = published().getFirst();
        assertThat(created.get("type").asText()).isEqualTo("OrderCreated");
        assertThat(created.at("/payload/total").decimalValue()).isEqualByComparingTo("288.80");
        assertThat(created.at("/payload/items").size()).isEqualTo(2);
    }

    @Test
    void happyPathEndsApproved() {
        String id = placeOrder();

        receive(id, "StockReserved", "PaymentCompleted", "ShipmentScheduled");

        given().get("/api/orders/" + id).then()
            .body("status", equalTo("APPROVED"))
            .body("timeline", hasSize(4));
    }

    @Test
    void noStockEndsRejected() {
        String id = placeOrder();
        receive(id, "StockReservationFailed");
        assertThat(status(id)).isEqualTo("REJECTED");
    }

    @Test
    void aDeclinedPaymentEndsRejectedAndRecordsTheCompensation() {
        String id = placeOrder();

        receive(id, "StockReserved", "PaymentFailed", "StockReleased");

        given().get("/api/orders/" + id).then()
            .body("status", equalTo("REJECTED"))
            .body("timeline[-1].eventType", equalTo("StockReleased"));
    }

    @Test
    void aFailedShipmentAfterThePivotEndsCancelled() {
        String id = placeOrder();
        receive(id, "StockReserved", "PaymentCompleted", "ShipmentFailed");
        assertThat(status(id)).isEqualTo("CANCELLED");
    }

    @Test
    void anEventThatDoesNotFitTheCurrentStateIsIgnored() {
        String id = placeOrder();

        receive(id, "ShipmentScheduled");   // llegó antes de tiempo (S8)

        given().get("/api/orders/" + id).then()
            .body("status", equalTo("PENDING"))
            .body("timeline[-1].applied", equalTo(false));
    }

    @Test
    void aDuplicateEventIsAppliedOnce() {
        String id = placeOrder();
        String duplicated = event("StockReserved", id);

        saga.on(duplicated);
        saga.on(duplicated);

        given().get("/api/orders/" + id).then().body("timeline", hasSize(2));
    }

    @Test
    void canBeCancelledBeforeThePivot() throws Exception {
        String id = placeOrder();
        receive(id, "StockReserved");

        given().post("/api/orders/" + id + "/cancel").then().statusCode(200).body("status", equalTo("CANCELLED"));

        assertThat(published()).extracting(e -> e.get("type").asText()).contains("OrderCancelled");
    }

    @Test
    void cannotBeCancelledAfterThePivot() {
        String id = placeOrder();
        receive(id, "StockReserved", "PaymentCompleted");

        given().post("/api/orders/" + id + "/cancel").then().statusCode(409);
    }

    @Test
    void consumesFromTheKafkaChannel() {
        String id = placeOrder();

        connector.source("saga-in").send(event("StockReserved", id));

        await().atMost(Duration.ofSeconds(10)).until(() -> "STOCK_RESERVED".equals(status(id)));
    }
}
