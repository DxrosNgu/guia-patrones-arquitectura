package com.mercadoinventos.orders;

import com.mercadoinventos.orders.clients.InventoryClient;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.Mockito.when;

@QuarkusTest
@WithTestResource(InMemoryKafka.class)
class AsyncOrdersTest {

    static final Map<String, Object> ORDER = Map.of(
        "customerId", "ana",
        "items", List.of(Map.of("sku", "INV-001", "quantity", 2)));

    @Inject
    @Any
    InMemoryConnector connector;

    @InjectMock
    @RestClient
    InventoryClient inventory;

    @BeforeEach
    void setUp() {
        connector.sink("orders-events").clear();
        when(inventory.stock("INV-001")).thenReturn(new InventoryClient.StockLevel("INV-001", 10));
    }

    @Test
    void acceptsTheOrderAndPublishesOrderPlaced() {
        given().contentType(ContentType.JSON).body(ORDER)
            .when().post("/api/orders")
            .then()
            .statusCode(202)
            .body("status", equalTo("PLACED"));

        assertThat(connector.sink("orders-events").received()).hasSize(1);
    }

    @Test
    void returnsTheTraceIdSoYouCanFindTheRequestInGrafana() {
        given().contentType(ContentType.JSON).body(ORDER)
            .when().post("/api/orders")
            .then()
            .statusCode(202)
            .header("X-Trace-Id", matchesPattern("[0-9a-f]{32}"));
    }

    @Test
    void rejectsImmediatelyWhenThereIsNotEnoughStock() {
        when(inventory.stock("INV-001")).thenReturn(new InventoryClient.StockLevel("INV-001", 1));

        given().contentType(ContentType.JSON).body(ORDER)
            .when().post("/api/orders")
            .then()
            .statusCode(409);

        assertThat(connector.sink("orders-events").received()).isEmpty();
    }

    @Test
    void answers503WhenInventoryCannotBeAsked() {
        when(inventory.stock("INV-001")).thenThrow(new ProcessingException("Connection refused"));

        given().contentType(ContentType.JSON).body(ORDER)
            .when().post("/api/orders")
            .then()
            .statusCode(503);
    }

    @Test
    void livenessAndReadinessAreSeparateEndpoints() {
        given().when().get("/q/health/live").then().statusCode(200);
        given().when().get("/q/health/ready").then().statusCode(200);
    }
}
