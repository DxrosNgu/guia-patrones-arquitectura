package com.mercadoinventos.payments;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.smallrye.faulttolerance.api.CircuitBreakerMaintenance;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(FakeProvider.class)
class ResilienceTest {

    @ConfigProperty(name = "quarkus.rest-client.provider.url")
    String provider;

    @Inject
    CircuitBreakerMaintenance circuits;

    @Inject
    PaymentService payments;

    @BeforeEach
    void reset() {
        given().post(provider + "/admin/reset").then().statusCode(200);
        circuits.resetAll();
    }

    private void chaos(String json) {
        given().contentType(ContentType.JSON).body(json).put(provider + "/admin/chaos").then().statusCode(200);
    }

    private int providerRequests() {
        return given().get(provider + "/admin/stats").then().extract().path("requests");
    }

    private static Response pay(String path, double amount) {
        return given().contentType(ContentType.JSON)
            .body("{\"orderId\":\"o-" + UUID.randomUUID() + "\",\"amount\":" + amount + "}")
            .post("/api/payments" + path);
    }

    @Test
    void aHealthyProviderApproves() {
        pay("", 100).then().statusCode(201).body("status", equalTo("APPROVED"));
    }

    @Test
    void aDeclinedPaymentIsABusinessErrorNotRetriedNorHidden() {
        pay("", 5000).then().statusCode(402).body("status", equalTo("DECLINED"));
        assertThat(providerRequests()).isEqualTo(1);
    }

    @Test
    void aSlowProviderIsCutByTheTimeoutAndFallsBack() {
        chaos("{\"latencyMs\":3000}");

        long start = System.nanoTime();
        pay("", 100).then().statusCode(202).body("status", equalTo("PENDING_CONFIRMATION"));
        long tookMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(tookMs).isLessThan(2500);         // 3 intentos de 500 ms + esperas, nunca 3 s cada uno
        assertThat(providerRequests()).isEqualTo(3);  // 1 + 2 reintentos
    }

    @Test
    void aDownProviderOpensTheCircuitAndStopsReceivingCalls() {
        chaos("{\"down\":true}");
        pay("", 100).then().statusCode(202);
        pay("", 100).then().statusCode(202);
        int before = providerRequests();

        long start = System.nanoTime();
        pay("", 100).then().statusCode(202).body("status", equalTo("PENDING_CONFIRMATION"));
        long tookMs = (System.nanoTime() - start) / 1_000_000;

        given().get("/api/resilience").then().body("circuit", equalTo("OPEN"));
        assertThat(providerRequests()).isEqualTo(before);   // con el circuito abierto, ni lo intenta
        assertThat(tookMs).isLessThan(300);                  // y responde al instante
    }

    @Test
    void pendingPaymentsAreConfirmedLaterWithTheSameKey() {
        chaos("{\"down\":true}");
        String orderId = "o-" + UUID.randomUUID();
        given().contentType(ContentType.JSON).body("{\"orderId\":\"" + orderId + "\",\"amount\":50}")
            .post("/api/payments").then().statusCode(202);

        chaos("{\"down\":false}");
        circuits.resetAll();

        // El reconciliador reintenta con la MISMA clave ("pay-" + pedido): la pasarela la reconoce.
        payments.reconcile();   // en la app corre solo, cada 5 s (@Scheduled)
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
            given().get("/api/payments/{id}", orderId).then()
                .body("status", equalTo("APPROVED"))
                .body("chargeId", equalTo("ch_pay-" + orderId)));
    }

    @Test
    void naiveRetriesMultiplyTheLoadOnAProviderThatIsAlreadyDown() {
        chaos("{\"down\":true}");

        pay("/naive-retry", 100).then().statusCode(502);

        assertThat(providerRequests()).isEqualTo(6);   // 1 + 5 reintentos inmediatos, para UN pago
    }

    @Test
    void theBulkheadLimitsConcurrentCallsToTheProvider() throws Exception {
        chaos("{\"latencyMs\":300}");
        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Future<Response>> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            futures.add(pool.submit((Callable<Response>) () -> pay("", 10)));
        }
        List<String> statuses = new ArrayList<>();
        for (Future<Response> f : futures) {
            statuses.add(f.get().jsonPath().getString("status"));
        }
        pool.shutdown();

        assertThat(statuses).contains("APPROVED", "PENDING_CONFIRMATION");
        assertThat(statuses.stream().filter("APPROVED"::equals).count()).isLessThanOrEqualTo(5);
    }
}
