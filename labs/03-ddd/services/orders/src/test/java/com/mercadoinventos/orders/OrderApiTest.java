package com.mercadoinventos.orders;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

@QuarkusTest
class OrderApiTest {

    private static String newOrder() {
        return given().contentType(ContentType.JSON).body("{\"customerId\":\"ana\",\"currency\":\"PEN\"}")
                .post("/api/orders").then().statusCode(201).body("status", equalTo("DRAFT"))
                .extract().path("id");
    }

    private static io.restassured.response.ValidatableResponse addLine(String id, String json) {
        return given().contentType(ContentType.JSON).body(json).post("/api/orders/{id}/lines", id).then();
    }

    @Test
    void theAggregateKeepsItsInvariants() {
        String id = newOrder();
        addLine(id, "{\"sku\":\"INV-001\",\"unitPrice\":0.10,\"quantity\":1}").statusCode(200);
        addLine(id, "{\"sku\":\"INV-002\",\"unitPrice\":0.20,\"quantity\":1}").statusCode(200)
                .body("total", equalTo(0.30f));

        addLine(id, "{\"sku\":\"INV-001\",\"unitPrice\":0.10,\"quantity\":10}")
                .statusCode(422).body("code", equalTo("MAX_QUANTITY_EXCEEDED"));
        addLine(id, "{\"sku\":\"INV-003\",\"unitPrice\":1,\"currency\":\"USD\",\"quantity\":1}")
                .statusCode(422).body("code", equalTo("CURRENCY_MISMATCH"));

        given().post("/api/orders/{id}/confirm", id).then().statusCode(200).body("status", equalTo("CONFIRMED"));
        addLine(id, "{\"sku\":\"INV-004\",\"unitPrice\":1,\"quantity\":1}")
                .statusCode(409).body("code", equalTo("ORDER_NOT_EDITABLE"));
        given().post("/api/orders/{id}/ship", id).then().statusCode(409).body("code", equalTo("INVALID_TRANSITION"));

        given().get("/api/orders/{id}", id).then().statusCode(200)
                .body("lines", hasSize(2)).body("total", equalTo(0.30f));
        given().get("/api/events").then().body("orderId", hasItem(id));
    }

    @Test
    void theTotalCannotBeSentByTheClient() {
        String id = newOrder();
        addLine(id, "{\"sku\":\"INV-001\",\"unitPrice\":10,\"quantity\":2,\"total\":1}").statusCode(200)
                .body("total", equalTo(20.0f));
    }

    @Test
    void theAnemicVersionAcceptsAnything() {
        String id = given().contentType(ContentType.JSON)
                .body("{\"customerId\":\"ana\",\"lines\":[{\"sku\":\"INV-001\",\"unitPrice\":0.1,\"quantity\":1},"
                        + "{\"sku\":\"INV-002\",\"unitPrice\":0.2,\"quantity\":1}]}")
                .post("/api/anemic/orders").then().statusCode(201)
                .body("total", equalTo(0.30000000000000004f)) // double
                .extract().path("id");

        given().contentType(ContentType.JSON).body("{\"sku\":\"INV-003\",\"unitPrice\":5,\"quantity\":50}")
                .post("/api/anemic/orders/{id}/lines", id).then().statusCode(200)
                .body("lines", hasSize(3))
                .body("total", equalTo(0.30000000000000004f)); // ❌ no se recalculó

        given().contentType(ContentType.JSON).body("{\"status\":\"banana\"}")
                .put("/api/anemic/orders/{id}/status", id).then().statusCode(200).body("status", equalTo("banana"));
    }

    @Test
    void unknownOrderIs404() {
        given().get("/api/orders/{id}", java.util.UUID.randomUUID()).then().statusCode(404);
    }
}
