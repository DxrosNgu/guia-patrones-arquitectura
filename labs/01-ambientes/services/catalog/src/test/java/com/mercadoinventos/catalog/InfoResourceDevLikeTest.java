package com.mercadoinventos.catalog;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * El MISMO código con OTRA configuración: así se comporta en otro ambiente.
 * No hay ningún "if (prod)" en la aplicación; solo cambian los valores.
 */
@QuarkusTest
@TestProfile(InfoResourceDevLikeTest.DevLike.class)
class InfoResourceDevLikeTest {

    public static class DevLike implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "app.environment", "dev",
                "app.featured-enabled", "true",
                "quarkus.log.level", "DEBUG");
        }
    }

    @Test
    void reflectsTheInjectedConfiguration() {
        given()
            .when().get("/api/info")
            .then()
            .statusCode(200)
            .body("environment", equalTo("dev"))
            .body("featuredEnabled", equalTo(true))
            .body("logLevel", equalTo("DEBUG"));
    }
}
