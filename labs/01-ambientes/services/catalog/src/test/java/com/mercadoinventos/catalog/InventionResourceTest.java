package com.mercadoinventos.catalog;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;

@QuarkusTest
class InventionResourceTest {

    @Test
    void listsTheSeededInventions() {
        given()
            .when().get("/api/inventions")
            .then()
            .statusCode(200)
            .body("size()", greaterThanOrEqualTo(4))
            .body("sku", hasItem("INV-001"))
            .body("name", hasItem("Paraguas solar"));
    }

    @Test
    void returnsPriceAsDecimalWithCurrency() {
        given()
            .when().get("/api/inventions")
            .then()
            .statusCode(200)
            .body("find { it.sku == 'INV-001' }.price", org.hamcrest.Matchers.equalTo(49.90f))
            .body("find { it.sku == 'INV-001' }.currency", org.hamcrest.Matchers.equalTo("PEN"));
    }
}
