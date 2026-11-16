package com.mercadoinventos.payments;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
class IdempotencyKeyTest {

    private static Map<String, Object> payment(String orderId, double amount) {
        return Map.of("orderId", orderId, "customerId", "ana", "amount", amount, "currency", "PEN");
    }

    private static Response pay(String key, Map<String, Object> body) {
        var request = given().contentType(ContentType.JSON).body(body);
        if (key != null) {
            request = request.header("Idempotency-Key", key);
        }
        return request.when().post("/api/payments");
    }

    private static void assertPaymentsOfOrder(String orderId, int expected) {
        given().queryParam("orderId", orderId).when().get("/api/payments")
            .then().statusCode(200).body("$", hasSize(expected));
    }

    @Test
    void theSameKeyAndBodyChargesOnceAndReplaysTheResponse() {
        String orderId = UUID.randomUUID().toString();
        String key = UUID.randomUUID().toString();

        Response first = pay(key, payment(orderId, 100));
        Response second = pay(key, payment(orderId, 100));

        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(second.statusCode()).isEqualTo(201);
        assertThat(second.jsonPath().getString("id")).isEqualTo(first.jsonPath().getString("id"));
        assertThat(first.header("Idempotent-Replayed")).isNull();
        assertThat(second.header("Idempotent-Replayed")).isEqualTo("true");
        assertPaymentsOfOrder(orderId, 1);
    }

    @Test
    void reusingAKeyWithADifferentBodyIsRejected() {
        String key = UUID.randomUUID().toString();
        pay(key, payment(UUID.randomUUID().toString(), 100));

        pay(key, payment(UUID.randomUUID().toString(), 999))
            .then()
            .statusCode(422)
            .body("detail", equalTo("Esta Idempotency-Key ya se usó con otra petición"));
    }

    @Test
    void withoutAKeyEveryRetryChargesAgain() {
        String orderId = UUID.randomUUID().toString();

        pay(null, payment(orderId, 100));
        pay(null, payment(orderId, 100));

        assertPaymentsOfOrder(orderId, 2);
    }

    @Test
    void aDeclinedPaymentIsReplayedToo() {
        String orderId = UUID.randomUUID().toString();
        String key = UUID.randomUUID().toString();

        pay(key, payment(orderId, 900)).then().body("status", equalTo("DECLINED"));
        pay(key, payment(orderId, 900)).then().body("status", equalTo("DECLINED"));

        assertPaymentsOfOrder(orderId, 1);
    }

    @Test
    void fiveConcurrentRetriesWithTheSameKeyChargeOnce() throws Exception {
        String orderId = UUID.randomUUID().toString();
        String key = UUID.randomUUID().toString();
        ExecutorService pool = Executors.newFixedThreadPool(5);

        Callable<Response> doubleClick = () -> pay(key, payment(orderId, 100));
        List<Future<Response>> results = pool.invokeAll(List.of(doubleClick, doubleClick, doubleClick, doubleClick, doubleClick));
        pool.shutdown();

        List<String> ids = results.stream().map(f -> {
            try {
                return f.get().jsonPath().getString("id");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).distinct().toList();
        assertThat(ids).hasSize(1);
        assertPaymentsOfOrder(orderId, 1);
    }
}
