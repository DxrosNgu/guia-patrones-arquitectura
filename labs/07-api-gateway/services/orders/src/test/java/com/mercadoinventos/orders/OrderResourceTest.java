package com.mercadoinventos.orders;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

/**
 * Pedidos valida la identidad OTRA VEZ, aunque detrás del gateway ya se validó.
 * Si alguien llega directo al servicio (saltándose el gateway), igual lo frena.
 * Aquí la identidad se simula con @TestSecurity; el token real se prueba en smoke.sh.
 */
@QuarkusTest
class OrderResourceTest {

    private static final String ORDER = "{\"items\":[{\"sku\":\"INV-001\",\"quantity\":1}]}";

    @Test
    void withoutIdentityIs401() {
        given().get("/api/orders").then().statusCode(401);
        given().contentType(ContentType.JSON).body(ORDER).post("/api/orders").then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "beto")
    void withoutTheBuyerRoleIs403() {
        given().contentType(ContentType.JSON).body(ORDER).post("/api/orders").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "ana", roles = "buyer")
    void aBuyerPlacesAnOrderInHerOwnName() {
        given().contentType(ContentType.JSON).body("{\"customerId\":\"otra-persona\",\"items\":[{\"sku\":\"INV-001\",\"quantity\":2}]}")
            .post("/api/orders")
            .then().statusCode(201)
            .body("customer", equalTo("ana")); // el cliente sale del TOKEN, no del cuerpo

        given().get("/api/orders").then().statusCode(200).body("customer", everyItem(equalTo("ana")));
    }

    @Test
    @TestSecurity(user = "carla", roles = "buyer")
    void eachUserOnlySeesHerOwnOrders() {
        given().contentType(ContentType.JSON).body(ORDER).post("/api/orders").then().statusCode(201);

        given().get("/api/orders").then().statusCode(200).body("customer", everyItem(equalTo("carla")));
    }
}
