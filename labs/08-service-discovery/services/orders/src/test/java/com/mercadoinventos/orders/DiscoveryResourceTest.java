package com.mercadoinventos.orders;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(FakeInventories.class)
class DiscoveryResourceTest {

    @Test
    void asksForStockAndTellsWhichInstanceAnswered() {
        given().get("/api/discovery/consul/stock/INV-001").then().statusCode(200)
            .body("via", equalTo("consul")).body("sku", equalTo("INV-001")).body("available", equalTo(12));
    }

    @Test
    void storkSpreadsTheCallsAcrossTheInstances() {
        given().queryParam("calls", 6).get("/api/discovery/consul/spread").then().statusCode(200)
            .body("byInstance.inventory-a", equalTo(3))
            .body("byInstance.inventory-b", equalTo(3))
            .body("errors", equalTo(0));
    }

    @Test
    void aSingleDnsNameOnlyReachesWhatItResolvesTo() {
        given().queryParam("calls", 6).get("/api/discovery/dns/spread").then().statusCode(200)
            .body("byInstance.inventory-a", equalTo(6))
            .body("byInstance", not(hasKey("inventory-b")));
    }

    @Test
    void unknownStrategyIs404() {
        given().get("/api/discovery/magic/stock/INV-001").then().statusCode(404);
    }
}
