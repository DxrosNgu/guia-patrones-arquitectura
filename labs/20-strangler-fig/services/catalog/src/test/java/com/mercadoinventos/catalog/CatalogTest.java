package com.mercadoinventos.catalog;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(FakeLegacy.class)
class CatalogTest {

    @Test
    void at_startup_it_copies_the_legacy_catalog_and_serves_it_with_the_same_contract() {
        given().get("/api/catalog/products").then().statusCode(200)
            .header("X-Served-By", "catalog")
            .body("sku", contains("INV-001", "INV-004"));
        given().get("/api/catalog/products/INV-004").then().statusCode(200)
            .body("name", equalTo("Taza que avisa cuando se enfría")).body("price", equalTo(25.5f));
        given().get("/api/catalog/products/NOPE").then().statusCode(404);
    }

    @Test
    void importing_again_does_nothing_once_the_catalog_has_data() {
        given().post("/admin/import").then().statusCode(200).body("imported", equalTo(0));
    }
}
