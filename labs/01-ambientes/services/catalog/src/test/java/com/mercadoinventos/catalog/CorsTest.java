package com.mercadoinventos.catalog;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** La configuración de CORS de prod: solo el origen del frontend puede leer la API desde un navegador. */
@QuarkusTest
@TestProfile(CorsTest.ProdCors.class)
class CorsTest {

    public static class ProdCors implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.http.cors.enabled", "true",
                    "quarkus.http.cors.origins", "http://localhost:3200");
        }
    }

    @Test
    void theOfficialFrontendIsAllowed() {
        given().header("Origin", "http://localhost:3200")
                .when().get("/api/info")
                .then().statusCode(200)
                .header("Access-Control-Allow-Origin", equalTo("http://localhost:3200"));
    }

    @Test
    void anyOtherOriginIsNotAllowed() {
        given().header("Origin", "http://localhost:3299")
                .when().get("/api/info")
                .then().header("Access-Control-Allow-Origin", nullValue());
    }

    @Test
    void preflightFromAnotherOriginIsRejected() {
        given().header("Origin", "http://localhost:3299")
                .header("Access-Control-Request-Method", "GET")
                .when().options("/api/info")
                .then().header("Access-Control-Allow-Origin", nullValue());
    }
}
