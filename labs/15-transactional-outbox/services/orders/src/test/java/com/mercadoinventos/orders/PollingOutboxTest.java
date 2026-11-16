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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/** Modo por defecto: Transactional Outbox + Polling Publisher. */
@QuarkusTest
@WithTestResource(InMemoryKafka.class)
class PollingOutboxTest {

    static final Map<String, Object> ORDER = Map.of(
        "customerId", "ana", "items", List.of(Map.of("sku", "INV-001", "quantity", 2)));

    @Inject
    @Any
    InMemoryConnector connector;

    @Inject
    OutboxPublisher publisher;

    @BeforeEach
    void drain() {
        publisher.publishPending();          // vacía lo que dejaron otros tests
        connector.sink("orders-events").clear();
    }

    static String placeOrder() {
        return given().contentType(ContentType.JSON).body(ORDER)
            .post("/api/orders").then().statusCode(202).extract().path("id");
    }

    @Test
    void placingAnOrderOnlyWritesToTheDatabase() {
        String id = placeOrder();

        // Nada se publicó todavía: el evento está guardado en la outbox, en la MISMA transacción.
        assertThat(connector.sink("orders-events").received()).isEmpty();
        given().when().get("/api/outbox").then()
            .body("find { it.aggregateId == '" + id + "' }.publishedAt", org.hamcrest.Matchers.nullValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void thePublisherSendsPendingEventsAndMarksThem() throws Exception {
        String id = placeOrder();

        int sent = publisher.publishPending();

        InMemorySink<Record<String, String>> sink = connector.sink("orders-events");
        assertThat(sent).isEqualTo(1);
        Record<String, String> record = sink.received().getFirst().getPayload();
        JsonNode event = new ObjectMapper().readTree(record.value());
        assertThat(record.key()).isEqualTo(id);
        assertThat(event.get("type").asText()).isEqualTo("OrderPlaced");
        assertThat(event.at("/payload/items/0/sku").asText()).isEqualTo("INV-001");
        given().when().get("/api/outbox").then()
            .body("find { it.aggregateId == '" + id + "' }.publishedAt", org.hamcrest.Matchers.notNullValue());
    }

    @Test
    void anEventIsPublishedOnlyOnce() {
        placeOrder();
        publisher.publishPending();

        assertThat(publisher.publishPending()).isZero();
        assertThat(connector.sink("orders-events").received()).hasSize(1);
    }

    @Test
    void twoPublishersRunningAtTheSameTimeDoNotDuplicate() throws Exception {
        for (int i = 0; i < 20; i++) {
            placeOrder();
        }
        ExecutorService pool = Executors.newFixedThreadPool(2);
        pool.invokeAll(List.of(publisher::publishPending, publisher::publishPending));
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        // FOR UPDATE SKIP LOCKED: cada fila la toma UN solo publicador.
        assertThat(connector.sink("orders-events").received()).hasSize(20);
    }
}
