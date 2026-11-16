package com.mercadoinventos.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.smallrye.reactive.messaging.kafka.Record;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/** Event Notification: el evento solo avisa "pasó algo"; el consumidor debe preguntar el resto. */
@QuarkusTest
@WithTestResource(InMemoryKafka.class)
@TestProfile(NotificationEventsTest.Notification.class)
class NotificationEventsTest {

    public static class Notification implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("orders.event-style", "notification");
        }
    }

    @Inject
    @Any
    InMemoryConnector connector;

    @Test
    @SuppressWarnings("unchecked")
    void theEventCarriesNoItems() throws Exception {
        given().contentType(ContentType.JSON).body(AsyncOrdersTest.ORDER)
            .post("/api/orders").then().statusCode(202);

        InMemorySink<Record<String, String>> sink = connector.sink("orders-events");
        JsonNode event = new ObjectMapper().readTree(sink.received().getLast().getPayload().value());

        assertThat(event.get("type").asText()).isEqualTo("OrderPlaced");
        assertThat(event.get("payload").has("items")).isFalse();
    }
}
