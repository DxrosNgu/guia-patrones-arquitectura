package com.mercadoinventos.payments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercadoinventos.payments.saga.OutboxRelay;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
@WithTestResource(InMemoryKafka.class)
class PaymentsParticipantTest {

    @Inject
    PaymentsParticipant payments;

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
        given().contentType(ContentType.JSON).body(Map.of("delayMs", 0)).put("/api/chaos");
    }

    static String command(String type, String orderId, String payload) {
        return """
            {"eventId":"%s","type":"%s","aggregateId":"%s","occurredAt":"2026-11-16T10:00:00Z","payload":%s}"""
            .formatted(UUID.randomUUID(), type, orderId, payload);
    }

    static String charge(String orderId, double total) {
        return command("ChargePayment", orderId,
            "{\"customerId\":\"ana\",\"total\":%s,\"currency\":\"PEN\"}".formatted(total));
    }

    List<JsonNode> replies() throws Exception {
        relay.publishPending();
        InMemorySink<String> sink = connector.sink("saga-out");
        List<JsonNode> out = new ArrayList<>();
        for (var message : sink.received()) {
            String topic = message.getMetadata(OutgoingKafkaRecordMetadata.class).orElseThrow().getTopic();
            assertThat(topic).isEqualTo("saga.replies");
            out.add(json.readTree(message.getPayload()));
        }
        return out;
    }

    @Test
    void chargesAndReplies() throws Exception {
        payments.on(charge(UUID.randomUUID().toString(), 99.8));

        assertThat(replies()).extracting(r -> r.get("type").asText()).containsExactly("PaymentCompleted");
    }

    @Test
    void declinesOverTheLimit() throws Exception {
        payments.on(charge(UUID.randomUUID().toString(), 900));

        assertThat(replies()).extracting(r -> r.get("type").asText()).containsExactly("PaymentFailed");
    }

    @Test
    void aResentChargeCommandIsReconciledNotChargedAgain() throws Exception {
        String order = UUID.randomUUID().toString();

        payments.on(charge(order, 50));
        payments.on(charge(order, 50));     // el orquestador no recibió respuesta y reenvió

        assertThat(replies()).extracting(r -> r.get("type").asText())
            .containsExactly("PaymentCompleted", "PaymentCompleted");
        given().queryParam("orderId", order).get("/api/payments").then().body("$", org.hamcrest.Matchers.hasSize(1));
    }

    @Test
    void refundsACharge() throws Exception {
        String order = UUID.randomUUID().toString();
        payments.on(charge(order, 50));

        payments.on(command("RefundPayment", order, "{}"));

        JsonNode refund = replies().getLast();
        assertThat(refund.get("type").asText()).isEqualTo("PaymentRefunded");
        assertThat(refund.at("/payload/refunded").asBoolean()).isTrue();
    }

    @Test
    void aRefundWithoutChargeStillReplies() throws Exception {
        payments.on(command("RefundPayment", UUID.randomUUID().toString(), "{}"));

        JsonNode refund = replies().getFirst();
        assertThat(refund.get("type").asText()).isEqualTo("PaymentRefunded");
        assertThat(refund.at("/payload/refunded").asBoolean()).isFalse();
    }

    @Test
    void theChaosDelayCanBeChanged() {
        given().contentType(ContentType.JSON).body(Map.of("delayMs", 1500)).put("/api/chaos")
            .then().statusCode(200).body("delayMs", org.hamcrest.Matchers.equalTo(1500));
    }

    @Test
    void consumesFromTheCommandChannel() {
        String order = UUID.randomUUID().toString();

        connector.source("saga-in").send(charge(order, 10));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
            given().queryParam("orderId", order).get("/api/payments")
                .then().body("$", org.hamcrest.Matchers.hasSize(1)));
    }
}
