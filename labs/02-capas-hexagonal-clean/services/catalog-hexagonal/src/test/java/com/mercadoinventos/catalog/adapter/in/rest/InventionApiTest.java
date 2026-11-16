package com.mercadoinventos.catalog.adapter.in.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * El contrato HTTP, igual en las tres versiones del lab (layered, hexagonal y hexagonal-py).
 * Este test SÍ arranca Quarkus y un Postgres de Dev Services: es más lento.
 */
@QuarkusTest
class InventionApiTest {

    private static String inventor() {
        return "inv-" + UUID.randomUUID();
    }

    private static String create(String inventor, String name, String price, String imageUrl) {
        String image = imageUrl == null ? "null" : "\"" + imageUrl + "\"";
        return given().contentType(ContentType.JSON)
                .body("{\"inventorId\":\"" + inventor + "\",\"name\":\"" + name + "\",\"price\":" + price + ",\"imageUrl\":" + image + "}")
                .post("/api/inventions")
                .then().statusCode(201).body("status", equalTo("DRAFT"))
                .extract().path("id");
    }

    @Test
    void publishFlow() {
        String inventor = inventor();
        String id = create(inventor, "Paraguas solar", "49.9", null);

        given().post("/api/inventions/{id}/publish", id)
                .then().statusCode(422).body("code", equalTo("IMAGE_REQUIRED"));

        given().contentType(ContentType.JSON).body("{\"imageUrl\":\"https://img.example/p.png\"}")
                .put("/api/inventions/{id}/image", id)
                .then().statusCode(200);

        given().post("/api/inventions/{id}/publish", id)
                .then().statusCode(200).body("status", equalTo("PUBLISHED")).body("price", equalTo(49.90f));

        given().queryParam("inventorId", inventor).get("/api/inventions")
                .then().statusCode(200).body("$", hasSize(1));
    }

    @Test
    void businessRulesAreReportedWithACode() {
        String inventor = inventor();
        given().contentType(ContentType.JSON)
                .body("{\"inventorId\":\"" + inventor + "\",\"name\":\"Gratis\",\"price\":0}")
                .post("/api/inventions")
                .then().statusCode(422).body("code", equalTo("PRICE_MUST_BE_POSITIVE"));

        create(inventor, "A", "1", null);
        given().contentType(ContentType.JSON)
                .body("{\"inventorId\":\"" + inventor + "\",\"name\":\" a \",\"price\":1}")
                .post("/api/inventions")
                .then().statusCode(409).body("code", equalTo("NAME_ALREADY_USED"));

        create(inventor, "B", "1", null);
        create(inventor, "C", "1", null);
        given().contentType(ContentType.JSON)
                .body("{\"inventorId\":\"" + inventor + "\",\"name\":\"D\",\"price\":1}")
                .post("/api/inventions")
                .then().statusCode(409).body("code", equalTo("TOO_MANY_DRAFTS"));
    }

    @Test
    void unknownInventionIs404() {
        given().get("/api/inventions/{id}", UUID.randomUUID()).then().statusCode(404);
    }

    @Test
    void infoTellsTheArchitectureAndTheAdapter() {
        given().get("/api/info").then().statusCode(200)
                .body("architecture", equalTo("hexagonal"))
                .body("persistence", equalTo("postgres"));
    }
}
