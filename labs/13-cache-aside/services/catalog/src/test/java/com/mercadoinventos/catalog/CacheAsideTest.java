package com.mercadoinventos.catalog;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CacheAsideTest {

    @Inject
    InventionReader reader;

    @BeforeEach
    void reset() {
        reader.resetForTests();
    }

    private static Response read(String mode, String sku) {
        return given().queryParam("cache", mode).get("/api/inventions/{sku}", sku);
    }

    private static int dbQueries() {
        return given().get("/api/stats").then().extract().path("dbQueries");
    }

    @Test
    void withoutCacheEveryReadGoesToTheDatabase() {
        read("none", "INV-001").then().statusCode(200).header("X-Cache", "BYPASS");
        read("none", "INV-001").then().statusCode(200).header("X-Cache", "BYPASS");

        assertThat(dbQueries()).isEqualTo(2);
    }

    @Test
    void redisCacheAsideMissesOnceThenHits() {
        read("redis", "INV-002").then().header("X-Cache", "MISS");
        read("redis", "INV-002").then().header("X-Cache", "HIT");
        read("redis", "INV-002").then().header("X-Cache", "HIT");

        assertThat(dbQueries()).isEqualTo(1);
    }

    @Test
    void writingInvalidatesTheCache() {
        read("redis", "INV-003").then().header("X-Cache", "MISS");
        read("local", "INV-003").then().header("X-Cache", "MISS");

        given().contentType(ContentType.JSON).body("{\"price\": 99.99}").put("/api/inventions/INV-003/price")
            .then().statusCode(200);

        read("redis", "INV-003").then().header("X-Cache", "MISS").body("price", org.hamcrest.Matchers.equalTo(99.99f));
        read("local", "INV-003").then().header("X-Cache", "MISS").body("price", org.hamcrest.Matchers.equalTo(99.99f));
    }

    @Test
    void localCacheWorksTooButOnlyInsideThisInstance() {
        read("local", "INV-004").then().header("X-Cache", "MISS");
        read("local", "INV-004").then().header("X-Cache", "HIT");

        assertThat(dbQueries()).isEqualTo(1);
    }

    @Test
    void cacheResultDoesTheSameCacheAsideWithAnnotations() {
        read("annotated", "INV-002").then().statusCode(200).header("X-Cache", "MISS");
        read("annotated", "INV-002").then().statusCode(200).header("X-Cache", "HIT");
        given().contentType(ContentType.JSON).body("{\"price\":79.00}").put("/api/inventions/INV-002/price").then().statusCode(200);

        read("annotated", "INV-002").then().header("X-Cache", "MISS").body("price", org.hamcrest.Matchers.equalTo(79.0f));

        given().contentType(ContentType.JSON).body("{\"price\":89.00}").put("/api/inventions/INV-002/price").then().statusCode(200);
    }

    @Test
    void cacheResultLetsOnlyOneOfManyConcurrentCallsReachTheDatabase() throws Exception {
        assertThat(concurrentReads("annotated", "INV-003")).isEqualTo(1);
    }

    @Test
    void cacheResultAlsoCachesWhatDoesNotExist() {
        read("annotated", "NOPE").then().statusCode(404);
        read("annotated", "NOPE").then().statusCode(404);

        assertThat(dbQueries()).isEqualTo(1);   // Optional.empty() también quedó en la caché
    }

    @Test
    void unknownSkuIs404() {
        read("redis", "NOPE").then().statusCode(404);
    }

    @Test
    void stampedeProtectionLetsOnlyOneRequestRebuildAnExpiredKey() throws Exception {
        assertThat(concurrentMisses(true)).isEqualTo(1);
    }

    @Test
    void withoutProtectionAnExpiredHotKeyHitsTheDatabaseManyTimes() throws Exception {
        assertThat(concurrentMisses(false)).isGreaterThan(5);
    }

    private int concurrentMisses(boolean protection) throws Exception {
        given().contentType(ContentType.JSON).body("{\"enabled\":" + protection + "}").put("/admin/stampede-protection")
            .then().statusCode(200);
        return concurrentReads("redis", "INV-001");
    }

    private int concurrentReads(String mode, String sku) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(20);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            futures.add(pool.submit((Callable<Integer>) () -> read(mode, sku).statusCode()));
        }
        for (Future<Integer> f : futures) {
            assertThat(f.get()).isEqualTo(200);
        }
        pool.shutdown();
        return dbQueries();
    }
}
