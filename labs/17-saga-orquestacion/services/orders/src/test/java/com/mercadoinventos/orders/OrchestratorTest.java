package com.mercadoinventos.orders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercadoinventos.orders.saga.OutboxRelay;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
@WithTestResource(InMemoryKafka.class)
class OrchestratorTest {

    @Inject
    OrderSagaOrchestrator saga;

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

    // ── helpers ──────────────────────────────────────────────────────────

    static String placeOrder() {
        return given().contentType(ContentType.JSON)
            .body(Map.of("customerId", "ana", "items", List.of(
                Map.of("sku", "INV-001", "quantity", 2, "unitPrice", 49.90))))
            .post("/api/orders").then().statusCode(202).extract().path("id");
    }

    static String reply(String type, String orderId) {
        return """
            {"eventId":"%s","type":"%s","aggregateId":"%s","occurredAt":"2026-11-16T10:00:00Z","payload":{"reason":"prueba"}}"""
            .formatted(UUID.randomUUID(), type, orderId);
    }

    void receive(String orderId, String... types) {
        for (String type : types) {
            saga.on(reply(type, orderId));
        }
    }

    /** Comandos enviados para ESTE pedido desde la última limpieza, como "Tipo→topic". */
    List<String> commands(String orderId) throws Exception {
        relay.publishPending();
        InMemorySink<String> sink = connector.sink("saga-out");
        List<String> out = new ArrayList<>();
        for (var message : sink.received()) {
            var command = json.readTree(message.getPayload());
            if (!command.get("aggregateId").asText().equals(orderId)) {
                continue;   // los timeouts también reenvían comandos de sagas de otros tests
            }
            String topic = message.getMetadata(OutgoingKafkaRecordMetadata.class).orElseThrow().getTopic();
            out.add(command.get("type").asText() + "→" + topic);
        }
        return out;
    }

    Map<String, Object> view(String id) {
        return given().get("/api/orders/" + id).then().statusCode(200).extract().as(Map.class);
    }

    String sagaState(String id) {
        return (String) view(id).get("sagaState");
    }

    void timeoutsAfter(Duration elapsed) {
        saga.checkTimeouts(Instant.now().plus(elapsed));
    }

    // ── camino feliz y fallas ────────────────────────────────────────────

    @Test
    void startsByAskingInventoryToReserve() throws Exception {
        String id = placeOrder();

        assertThat(sagaState(id)).isEqualTo("RESERVING_STOCK");
        assertThat(commands(id)).containsExactly("ReserveStock→inventory.commands");
    }

    @Test
    void happyPath() throws Exception {
        String id = placeOrder();

        receive(id, "StockReserved", "PaymentCompleted", "ShipmentScheduled");

        assertThat(commands(id)).containsExactly(
            "ReserveStock→inventory.commands", "ChargePayment→payments.commands", "ScheduleShipment→shipping.commands");
        assertThat(view(id)).containsEntry("status", "APPROVED").containsEntry("sagaState", "APPROVED");
    }

    @Test
    void noStockRejectsWithoutCompensating() throws Exception {
        String id = placeOrder();
        receive(id, "StockReservationFailed");

        assertThat(view(id)).containsEntry("status", "REJECTED");
        assertThat(commands(id)).containsExactly("ReserveStock→inventory.commands");
    }

    @Test
    void aDeclinedPaymentReleasesTheStock() throws Exception {
        String id = placeOrder();
        receive(id, "StockReserved", "PaymentFailed");

        assertThat(sagaState(id)).isEqualTo("COMPENSATING");
        assertThat(commands(id)).last().isEqualTo("ReleaseStock→inventory.commands");

        receive(id, "StockReleased");
        assertThat(view(id)).containsEntry("status", "REJECTED");
    }

    @Test
    void aFailedShipmentCompensatesBothStepsAndWaitsForBoth() throws Exception {
        String id = placeOrder();
        receive(id, "StockReserved", "PaymentCompleted", "ShipmentFailed");

        assertThat(commands(id)).contains("ReleaseStock→inventory.commands", "RefundPayment→payments.commands");
        receive(id, "StockReleased");
        assertThat(sagaState(id)).isEqualTo("COMPENSATING");   // falta el reembolso
        receive(id, "PaymentRefunded");
        assertThat(view(id)).containsEntry("status", "CANCELLED");
    }

    // ── timeouts (S6, S13) ───────────────────────────────────────────────

    @Test
    void aSlowPaymentIsAskedAgainNotCompensated() throws Exception {
        String id = placeOrder();
        receive(id, "StockReserved");
        commands(id);
        connector.sink("saga-out").clear();

        timeoutsAfter(Duration.ofSeconds(6));

        assertThat(commands(id)).containsExactly("ChargePayment→payments.commands");   // reconciliar
        assertThat(view(id)).containsEntry("attempts", 2);
        receive(id, "PaymentCompleted");
        assertThat(sagaState(id)).isEqualTo("SCHEDULING_SHIPMENT");
    }

    @Test
    void aParticipantThatNeverAnswersExpiresTheSagaAndCompensates() throws Exception {
        String id = placeOrder();
        receive(id, "StockReserved");

        for (int i = 1; i <= 3; i++) {
            timeoutsAfter(Duration.ofSeconds(6L * i));
        }

        assertThat(sagaState(id)).isEqualTo("COMPENSATING");
        assertThat(commands(id)).contains("ReleaseStock→inventory.commands", "RefundPayment→payments.commands");
        receive(id, "StockReleased", "PaymentRefunded");
        assertThat(view(id)).containsEntry("status", "EXPIRED");
    }

    @Test
    void afterThePivotATimeoutIsRetriedForever() throws Exception {
        String id = placeOrder();
        receive(id, "StockReserved", "PaymentCompleted");

        for (int i = 1; i <= 6; i++) {
            timeoutsAfter(Duration.ofSeconds(6L * i));
        }

        assertThat(sagaState(id)).isEqualTo("SCHEDULING_SHIPMENT");
        assertThat(commands(id)).filteredOn(c -> c.startsWith("ScheduleShipment")).hasSize(7);
        assertThat(commands(id)).noneMatch(c -> c.startsWith("Refund") || c.startsWith("Release"));
    }

    // ── orden, duplicados y cancelación ─────────────────────────────────

    @Test
    void aReplyThatDoesNotFitTheCurrentStepIsIgnored() {
        String id = placeOrder();

        receive(id, "PaymentCompleted");

        assertThat(sagaState(id)).isEqualTo("RESERVING_STOCK");
        List<Map<String, Object>> timeline = (List<Map<String, Object>>) view(id).get("timeline");
        assertThat(timeline.getLast()).containsEntry("applied", false);
    }

    @Test
    void aDuplicateReplyIsAppliedOnce() throws Exception {
        String id = placeOrder();
        String stockReserved = reply("StockReserved", id);

        saga.on(stockReserved);
        saga.on(stockReserved);

        assertThat(commands(id)).filteredOn(c -> c.startsWith("ChargePayment")).hasSize(1);
    }

    @Test
    void cancellingWhileChargingCompensatesAndIgnoresTheLatePayment() throws Exception {
        String id = placeOrder();
        receive(id, "StockReserved");

        given().post("/api/orders/" + id + "/cancel").then().statusCode(200);
        receive(id, "PaymentCompleted", "StockReleased", "PaymentRefunded");

        assertThat(commands(id)).contains("ReleaseStock→inventory.commands", "RefundPayment→payments.commands");
        assertThat(view(id)).containsEntry("status", "CANCELLED");
    }

    @Test
    void cannotCancelAfterThePivot() {
        String id = placeOrder();
        receive(id, "StockReserved", "PaymentCompleted");

        given().post("/api/orders/" + id + "/cancel").then().statusCode(409);
    }

    @Test
    void consumesRepliesFromKafka() {
        String id = placeOrder();

        connector.source("saga-in").send(reply("StockReserved", id));

        await().atMost(Duration.ofSeconds(10)).until(() -> "CHARGING".equals(sagaState(id)));
    }
}
