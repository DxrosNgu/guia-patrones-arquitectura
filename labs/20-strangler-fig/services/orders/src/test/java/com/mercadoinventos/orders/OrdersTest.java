package com.mercadoinventos.orders;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(FakeNeighbours.class)
class OrdersTest {

    @ConfigProperty(name = "fake.url")
    String fake;

    @AfterEach
    void syncOn() {
        given().contentType(ContentType.JSON).body("{\"enabled\":true}").put("/admin/sync").then().statusCode(200);
    }

    private long place(String customer, String sku, int quantity) {
        return given().contentType(ContentType.JSON).body(Map.of("customer", customer, "sku", sku, "quantity", quantity))
            .post("/api/orders").then().statusCode(201).header("X-Served-By", "orders")
            .extract().<Number>path("id").longValue();
    }

    private List<String> receivedByLegacy() {
        return given().baseUri(fake).get("/received").then().extract().path("$");
    }

    @Test
    void new_orders_use_their_own_id_range_and_the_new_catalog_price() {
        long id = place("carla", "INV-001", 3);

        assertThat(id).isGreaterThanOrEqualTo(500001);
        given().get("/api/orders/" + id).then().statusCode(200)
            .body("customer", equalTo("carla")).body("total", equalTo(149.70f));
        given().contentType(ContentType.JSON).body(Map.of("customer", "carla", "sku", "NOPE", "quantity", 1))
            .post("/api/orders").then().statusCode(422);
    }

    @Test
    void orders_created_here_are_copied_to_the_legacy() throws InterruptedException {
        long id = place("dani", "INV-001", 1);

        waitUntil(() -> receivedByLegacy().contains(String.valueOf(id)));
        given().get("/admin/sync").then().body("pendingToLegacy", equalTo(0));
    }

    @Test
    void orders_created_in_the_legacy_are_copied_here() throws InterruptedException {
        waitUntil(() -> given().get("/api/orders/1001").statusCode() == 200);

        given().get("/api/orders/1002").then().statusCode(200).body("total", equalTo(25.5f));
        List<Integer> ids = given().queryParam("customer", "ana").get("/api/orders").then().extract().path("id");
        assertThat(ids).contains(1001, 1002);
    }

    @Test
    void with_the_sync_off_new_orders_stay_pending() throws InterruptedException {
        given().contentType(ContentType.JSON).body("{\"enabled\":false}").put("/admin/sync").then().statusCode(200);
        Thread.sleep(1500);   // deja terminar una pasada que ya estuviera en curso
        long id = place("eva", "INV-001", 1);
        Thread.sleep(2500);

        assertThat(receivedByLegacy()).doesNotContain(String.valueOf(id));
        given().get("/admin/sync").then().body("pendingToLegacy", equalTo(1));
    }

    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("No ocurrió a tiempo");
    }
}
