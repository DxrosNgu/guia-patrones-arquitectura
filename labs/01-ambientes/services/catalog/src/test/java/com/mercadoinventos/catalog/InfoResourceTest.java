package com.mercadoinventos.catalog;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/** Sin configuración extra: valores del perfil de test. */
@QuarkusTest
class InfoResourceTest {

    @Test
    void reportsTheEnvironmentAndDefaults() {
        given()
            .when().get("/api/info")
            .then()
            .statusCode(200)
            .body("service", equalTo("catalog"))
            .body("environment", equalTo("test"))
            .body("featuredEnabled", equalTo(false))
            .body("logLevel", equalTo("INFO"));
    }
}
