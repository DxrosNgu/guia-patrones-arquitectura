package com.mercadoinventos.payments;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsInAnyOrder;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PaymentResourceTest {

    @Test
    void returnsThePaymentsOfSeveralOrdersInOneCall() {
        given().queryParam("orderIds", "ord-1001,ord-1003,ord-9999").get("/api/payments").then().statusCode(200)
            .body("orderId", containsInAnyOrder("ord-1001", "ord-1003"))
            .body("status", containsInAnyOrder("PAID", "PENDING"));
    }

    @Test
    void orderIdsAreRequired() {
        given().get("/api/payments").then().statusCode(400);
    }
}
