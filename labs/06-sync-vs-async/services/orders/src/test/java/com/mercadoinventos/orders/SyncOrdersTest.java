package com.mercadoinventos.orders;

import com.mercadoinventos.orders.clients.InventoryClient;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Modo síncrono: el pedido ESPERA la respuesta de inventario. */
@QuarkusTest
@WithTestResource(InMemoryKafka.class)
@TestProfile(SyncOrdersTest.Sync.class)
class SyncOrdersTest {

    public static class Sync implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("orders.mode", "sync");
        }
    }

    @InjectMock
    @RestClient
    InventoryClient inventory;

    private io.restassured.response.ValidatableResponse placeOrder() {
        return given().contentType(ContentType.JSON).body(AsyncOrdersTest.ORDER)
            .when().post("/api/orders").then();
    }

    @Test
    void confirmsWhenInventoryReserves() {
        when(inventory.reserve(any())).thenReturn(new InventoryClient.ReservationResult("x", "RESERVED"));

        placeOrder().statusCode(201).body("status", equalTo("CONFIRMED"));
    }

    @Test
    void rejectsWhenThereIsNoStock() {
        when(inventory.reserve(any())).thenThrow(new WebApplicationException(409));

        placeOrder().statusCode(201).body("status", equalTo("REJECTED"));
    }

    @Test
    void failsTheRequestWhenInventoryIsDown() {
        when(inventory.reserve(any())).thenThrow(new ProcessingException("Connection refused"));

        placeOrder()
            .statusCode(503)
            .body("detail", containsString("inventario no responde"));
    }
}
