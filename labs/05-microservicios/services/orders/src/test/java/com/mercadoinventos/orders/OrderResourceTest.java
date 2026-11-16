package com.mercadoinventos.orders;

import com.mercadoinventos.orders.clients.InventoryClient;
import com.mercadoinventos.orders.clients.PaymentsClient;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class OrderResourceTest {

    @InjectMock
    @RestClient
    InventoryClient inventory;

    @InjectMock
    @RestClient
    PaymentsClient payments;

    private static final Map<String, Object> ORDER = Map.of(
        "customerId", "ana",
        "currency", "PEN",
        "items", List.of(
            Map.of("sku", "INV-001", "quantity", 2, "unitPrice", 49.90),
            Map.of("sku", "INV-003", "quantity", 1, "unitPrice", 189.00)));

    private io.restassured.response.ValidatableResponse placeOrder() {
        return given().contentType(ContentType.JSON).body(ORDER)
            .when().post("/api/orders")
            .then();
    }

    @Test
    void confirmsTheOrderWhenStockAndPaymentSucceed() {
        when(inventory.reserve(any())).thenReturn(new InventoryClient.ReservationResult("x", "RESERVED"));
        when(payments.charge(any())).thenReturn(new PaymentsClient.PaymentResult("p1", "APPROVED", null));

        placeOrder()
            .statusCode(201)
            .body("status", equalTo("CONFIRMED"))
            .body("total", equalTo(288.80f))
            .body("lines", hasSize(2));
    }

    @Test
    void rejectsTheOrderWhenThereIsNoStock() {
        when(inventory.reserve(any())).thenThrow(new WebApplicationException(409));

        placeOrder()
            .statusCode(201)
            .body("status", equalTo("REJECTED"))
            .body("rejectionReason", startsWith("Sin stock"));
        verify(payments, never()).charge(any());
    }

    @Test
    void rejectsTheOrderWhenThePaymentIsDeclined() {
        when(inventory.reserve(any())).thenReturn(new InventoryClient.ReservationResult("x", "RESERVED"));
        when(payments.charge(any())).thenReturn(
            new PaymentsClient.PaymentResult("p1", "DECLINED", "Supera el límite de la tarjeta (500.00)"));

        placeOrder()
            .statusCode(201)
            .body("status", equalTo("REJECTED"))
            .body("rejectionReason", equalTo("Pago rechazado: Supera el límite de la tarjeta (500.00)"));
    }

    @Test
    void rejectsTheOrderWhenPaymentsIsDown() {
        when(inventory.reserve(any())).thenReturn(new InventoryClient.ReservationResult("x", "RESERVED"));
        when(payments.charge(any())).thenThrow(new ProcessingException("Connection refused"));

        placeOrder()
            .statusCode(201)
            .body("status", equalTo("REJECTED"))
            .body("rejectionReason", equalTo("El servicio de pagos no responde"));
    }

    @Test
    void returnsAStoredOrderWithItsLines() {
        when(inventory.reserve(any())).thenReturn(new InventoryClient.ReservationResult("x", "RESERVED"));
        when(payments.charge(any())).thenReturn(new PaymentsClient.PaymentResult("p1", "APPROVED", null));
        String id = placeOrder().extract().path("id");

        given().when().get("/api/orders/" + id)
            .then()
            .statusCode(200)
            .body("id", equalTo(id))
            .body("lines.sku", org.hamcrest.Matchers.hasItems("INV-001", "INV-003"));
    }

    @Test
    void unknownOrderIsNotFound() {
        given().when().get("/api/orders/00000000-0000-0000-0000-000000000000")
            .then().statusCode(404);
    }

    @Test
    void rejectsAnOrderWithoutItems() {
        given().contentType(ContentType.JSON)
            .body(Map.of("customerId", "ana", "currency", "PEN", "items", List.of()))
            .when().post("/api/orders")
            .then().statusCode(400);
    }
}
