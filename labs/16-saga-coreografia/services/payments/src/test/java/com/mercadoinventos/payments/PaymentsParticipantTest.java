package com.mercadoinventos.payments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercadoinventos.payments.saga.OutboxRelay;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
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
    }

    static String event(String type, String orderId, String payload) {
        return """
            {"eventId":"%s","type":"%s","aggregateId":"%s","occurredAt":"2026-11-16T10:00:00Z","payload":%s}"""
            .formatted(UUID.randomUUID(), type, orderId, payload);
    }

    static String stockReserved(String orderId, double total) {
        return event("StockReserved", orderId, """
            {"customerId":"ana","total":%s,"currency":"PEN","items":[{"sku":"INV-001","quantity":1,"unitPrice":%s}]}"""
            .formatted(total, total));
    }

    /** Publica la outbox y devuelve [tipo, topic] de cada mensaje enviado. */
    List<String[]> published() {
        relay.publishPending();
        InMemorySink<String> sink = connector.sink("saga-out");
        return sink.received().stream().map(this::typeAndTopic).toList();
    }

    String[] typeAndTopic(Message<String> message) {
        try {
            JsonNode event = json.readTree(message.getPayload());
            String topic = message.getMetadata(OutgoingKafkaRecordMetadata.class).orElseThrow().getTopic();
            return new String[]{event.get("type").asText(), topic};
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void chargesWhenStockIsReserved() {
        String order = UUID.randomUUID().toString();

        payments.on(stockReserved(order, 99.80));

        assertThat(published()).singleElement()
            .satisfies(e -> assertThat(e).containsExactly("PaymentCompleted", "payments.events"));
    }

    @Test
    void declinesOverTheLimit() {
        payments.on(stockReserved(UUID.randomUUID().toString(), 900));

        assertThat(published()).singleElement().satisfies(e -> assertThat(e[0]).isEqualTo("PaymentFailed"));
    }

    @Test
    void refundsWhenTheShipmentFails() {
        String order = UUID.randomUUID().toString();
        payments.on(stockReserved(order, 50));

        payments.on(event("ShipmentFailed", order, "{\"reason\":\"sin cobertura\"}"));

        assertThat(published()).extracting(e -> e[0]).containsExactly("PaymentCompleted", "PaymentRefunded");
        given().queryParam("orderId", order).get("/api/payments")
            .then().body("[0].status", org.hamcrest.Matchers.equalTo("REFUNDED"));
    }

    @Test
    void aDuplicateEventChargesOnce() {
        String duplicated = stockReserved(UUID.randomUUID().toString(), 50);

        payments.on(duplicated);
        payments.on(duplicated);

        assertThat(published()).hasSize(1);
    }

    @Test
    void doesNotChargeAnOrderItAlreadyKnowsIsCancelled() {
        String order = UUID.randomUUID().toString();

        payments.on(event("OrderCancelled", order, "{\"reason\":\"cliente\"}"));
        payments.on(stockReserved(order, 50));

        assertThat(published()).isEmpty();
    }

    @Test
    void refundsWhenTheCancellationArrivesAfterTheCharge() {
        String order = UUID.randomUUID().toString();
        payments.on(stockReserved(order, 50));

        payments.on(event("OrderCancelled", order, "{\"reason\":\"cliente\"}"));

        assertThat(published()).extracting(e -> e[0]).containsExactly("PaymentCompleted", "PaymentRefunded");
    }

    @Test
    void consumesFromTheKafkaChannel() {
        String order = UUID.randomUUID().toString();

        connector.source("saga-in").send(stockReserved(order, 10));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
            given().queryParam("orderId", order).get("/api/payments")
                .then().body("$", org.hamcrest.Matchers.hasSize(1)));
    }
}
