package com.mercadoinventos.shop;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ShopTest {

    private static int stock(String sku) {
        List<Map<String, Object>> rows = given().get("/api/inventory/stock").then().statusCode(200)
                .extract().jsonPath().getList("$");
        return rows.stream().filter(r -> r.get("sku").equals(sku)).map(r -> (Integer) r.get("available"))
                .findFirst().orElseThrow();
    }

    private static io.restassured.response.ValidatableResponse order(String customer, String sku, int quantity) {
        return given().contentType(ContentType.JSON)
                .body("{\"customerId\":\"" + customer + "\",\"items\":[{\"sku\":\"" + sku + "\",\"quantity\":" + quantity + "}]}")
                .post("/api/orders").then();
    }

    @Test
    void anOrderTouchesFourModulesInOneTransaction() {
        int before = stock("INV-004");

        String id = order("ana", "INV-004", 2).statusCode(201)
                .body("status", equalTo("CONFIRMED")).body("total", equalTo(51.0f))
                .extract().path("id");

        assertThat(stock("INV-004")).isEqualTo(before - 2);
        given().get("/api/payments").then().body("orderId", org.hamcrest.Matchers.hasItem(id));
        given().get("/api/shipments").then().body("orderId", org.hamcrest.Matchers.hasItem(id));
    }

    @Test
    void aDeclinedPaymentRollsBackTheStockReservation() {
        int before = stock("INV-003");

        order("beto", "INV-003", 5).statusCode(402).body("code", equalTo("PAYMENT_DECLINED")); // 600 > 500

        assertThat(stock("INV-003")).isEqualTo(before); // ¡el stock NO quedó reservado!
    }

    @Test
    void outOfStockIsAConflict() {
        order("ana", "INV-002", 1).statusCode(409).body("code", equalTo("OUT_OF_STOCK"));
    }
}
