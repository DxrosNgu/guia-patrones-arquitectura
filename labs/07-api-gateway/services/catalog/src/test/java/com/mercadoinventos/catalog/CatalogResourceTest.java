package com.mercadoinventos.catalog;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CatalogResourceTest {

    @Test
    void theCatalogIsPublic() {
        given().get("/api/catalog/inventions").then().statusCode(200).body("sku", hasItem("INV-001"));
    }

    @Test
    void unknownInventionIs404() {
        given().get("/api/catalog/inventions/NOPE").then().statusCode(404);
    }
}
