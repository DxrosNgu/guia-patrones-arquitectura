package com.mercadoinventos.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StranglerRoutesTest {

    static final FakeBackend LEGACY = new FakeBackend("legacy");
    static final FakeBackend CATALOG = new FakeBackend("catalog");
    static final FakeBackend ORDERS = new FakeBackend("orders");

    @DynamicPropertySource
    static void backends(DynamicPropertyRegistry registry) {
        registry.add("LEGACY_URL", LEGACY::url);
        registry.add("CATALOG_URL", CATALOG::url);
        registry.add("ORDERS_URL", ORDERS::url);
    }

    @Value("${local.server.port}")
    int port;

    WebTestClient client;

    @BeforeEach
    void stageOne() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        stage(1);
    }

    private void stage(int stage) {
        client.put().uri("/admin/migration/stage/" + stage).exchange().expectStatus().isOk();
    }

    private String servedBy(String path) {
        return client.get().uri(path).exchange().expectStatus().isOk()
            .expectBody(Map.class).returnResult().getResponseBody().get("servedBy").toString();
    }

    private long toNewOrders(int requests) {
        return IntStream.range(0, requests).filter(i -> servedBy("/api/orders/1001").equals("orders")).count();
    }

    @Test
    void stage_1_is_a_transparent_proxy_to_the_legacy() {
        assertThat(servedBy("/api/catalog/products")).isEqualTo("legacy");
        assertThat(servedBy("/api/orders/1001")).isEqualTo("legacy");
        assertThat(servedBy("/api/anything-else")).isEqualTo("legacy");
        client.get().uri("/api/catalog/products").exchange().expectHeader().valueEquals("X-Route", "legacy");
    }

    @Test
    void stage_2_moves_only_the_catalog() {
        stage(2);

        assertThat(servedBy("/api/catalog/products/INV-001")).isEqualTo("catalog");
        assertThat(servedBy("/api/orders/1001")).isEqualTo("legacy");
    }

    @Test
    void stage_3_sends_about_10_percent_of_orders_to_the_new_service() {
        stage(3);

        long toNew = toNewOrders(400);

        assertThat(toNew).isBetween(15L, 75L);   // 40 esperados; el sorteo es aleatorio
    }

    @Test
    void weights_change_and_roll_back_without_restarting() {
        client.put().uri("/admin/migration").bodyValue(Map.of("catalog", "NEW", "ordersToNewPercent", 50))
            .exchange().expectStatus().isOk();
        assertThat(toNewOrders(200)).isBetween(60L, 140L);

        client.put().uri("/admin/migration").bodyValue(Map.of("ordersToNewPercent", 0)).exchange().expectStatus().isOk();

        assertThat(toNewOrders(50)).isZero();
    }

    @Test
    void stage_5_turns_the_legacy_off() {
        stage(5);

        assertThat(servedBy("/api/orders/1001")).isEqualTo("orders");
        assertThat(servedBy("/api/catalog/products")).isEqualTo("catalog");
        client.get().uri("/api/anything-else").exchange().expectStatus().isNotFound();
    }

    @Test
    void the_legacy_cannot_be_turned_off_while_it_still_gets_traffic() {
        client.put().uri("/admin/migration").bodyValue(Map.of("legacyEnabled", false)).exchange()
            .expectStatus().isBadRequest();
    }
}
