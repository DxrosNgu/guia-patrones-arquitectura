package com.mercadoinventos.catalog.adapter.in.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Mismo código, otro adaptador: solo cambia una propiedad de configuración. */
@QuarkusTest
@TestProfile(MemoryAdapterTest.MemoryProfile.class)
class MemoryAdapterTest {

    public static class MemoryProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("catalog.persistence", "memory");
        }
    }

    @Test
    void worksTheSameWithTheMemoryAdapter() {
        given().get("/api/info").then().body("persistence", equalTo("memory"));

        String id = given().contentType(ContentType.JSON)
                .body("{\"inventorId\":\"ana\",\"name\":\"Lámpara\",\"price\":10,\"imageUrl\":\"https://img.example/l.png\"}")
                .post("/api/inventions")
                .then().statusCode(201).extract().path("id");

        given().post("/api/inventions/{id}/publish", id).then().statusCode(200).body("status", equalTo("PUBLISHED"));
    }
}
