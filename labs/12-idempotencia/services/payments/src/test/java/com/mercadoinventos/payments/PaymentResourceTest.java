package com.mercadoinventos.payments;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class PaymentResourceTest {

    private static Map<String, Object> payment(String orderId, double amount) {
        return Map.of("orderId", orderId, "customerId", "ana", "amount", amount, "currency", "PEN");
    }

    @Test
    void approvesPaymentsUpToTheLimit() {
        given().contentType(ContentType.JSON).body(payment(UUID.randomUUID().toString(), 500.00))
            .when().post("/api/payments")
            .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("status", equalTo("APPROVED"));
    }

    @Test
    void declinesPaymentsOverTheLimit() {
        given().contentType(ContentType.JSON).body(payment(UUID.randomUUID().toString(), 500.01))
            .when().post("/api/payments")
            .then()
            .statusCode(201)
            .body("status", equalTo("DECLINED"))
            .body("reason", equalTo("Supera el límite de la tarjeta (500.00)"));
    }

    @Test
    void listsThePaymentsOfAnOrder() {
        String orderId = UUID.randomUUID().toString();
        given().contentType(ContentType.JSON).body(payment(orderId, 10)).post("/api/payments");

        given().queryParam("orderId", orderId)
            .when().get("/api/payments")
            .then()
            .statusCode(200)
            .body("$", hasSize(1))
            .body("[0].orderId", equalTo(orderId));
    }

    @Test
    void rejectsNonPositiveAmounts() {
        given().contentType(ContentType.JSON).body(payment(UUID.randomUUID().toString(), 0))
            .when().post("/api/payments")
            .then()
            .statusCode(400);
    }
}
