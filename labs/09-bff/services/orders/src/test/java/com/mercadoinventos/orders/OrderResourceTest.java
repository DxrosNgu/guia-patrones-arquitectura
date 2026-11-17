package com.mercadoinventos.orders;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class OrderResourceTest {

    @Test
    void listsTheOrdersOfACustomer() {
        given().queryParam("customerId", "ana").get("/api/orders").then().statusCode(200)
            .body("$", hasSize(3)).body("customerId", everyItem(equalTo("ana")));
    }

    @Test
    void customerIsRequired() {
        given().get("/api/orders").then().statusCode(400);
    }

    @Test
    void latencyCanBeInjected() {
        given().contentType("application/json").body("{\"ms\":0}").put("/admin/delay").then().statusCode(200).body("ms", equalTo(0));
    }
}
