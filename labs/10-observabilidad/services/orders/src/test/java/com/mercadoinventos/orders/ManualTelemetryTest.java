package com.mercadoinventos.orders;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

import com.mercadoinventos.orders.clients.InventoryClient;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Los spans que agregamos A MANO, además de los automáticos (HTTP, JDBC, Kafka). */
@QuarkusTest
@WithTestResource(InMemoryKafka.class)
class ManualTelemetryTest {

    static final Map<String, Object> ORDER = Map.of(
        "customerId", "ana",
        "items", List.of(Map.of("sku", "INV-001", "quantity", 2)));

    @Inject
    InMemorySpanExporter spans;

    @InjectMock
    @RestClient
    InventoryClient inventory;

    @BeforeEach
    void setUp() {
        spans.reset();
    }

    /** Filtra por traza: el exportador por lotes puede entregar spans de tests anteriores. */
    private Optional<SpanData> span(String name, String traceId) {
        return spans.getFinishedSpanItems().stream()
            .filter(s -> s.getName().equals(name) && s.getTraceId().equals(traceId)).findFirst();
    }

    @Test
    void annotationAndManualSpansHangFromTheRequestTrace() {
        when(inventory.stock("INV-001")).thenReturn(new InventoryClient.StockLevel("INV-001", 10));

        String traceId = given().contentType(ContentType.JSON).body(ORDER)
            .when().post("/api/orders")
            .then().statusCode(202).extract().header("X-Trace-Id");

        // Los dos spans hechos a mano pertenecen a la MISMA traza que la petición HTTP.
        await().atMost(Duration.ofSeconds(10)).until(() ->
            span("orders.check-stock", traceId).isPresent() && span("orders.save-and-publish", traceId).isPresent());
        SpanData check = span("orders.check-stock", traceId).orElseThrow();      // @WithSpan
        SpanData save = span("orders.save-and-publish", traceId).orElseThrow();  // Tracer a mano
        assertThat(check.getAttributes().get(AttributeKey.longKey("order.items"))).isEqualTo(1L);
        assertThat(save.getAttributes().get(AttributeKey.stringKey("order.id"))).isNotBlank();
    }

    @Test
    void aRejectedOrderMarksTheSpanAsError() {
        when(inventory.stock("INV-001")).thenReturn(new InventoryClient.StockLevel("INV-001", 1));

        String traceId = given().contentType(ContentType.JSON).body(ORDER).when().post("/api/orders")
            .then().statusCode(409).extract().header("X-Trace-Id");

        await().atMost(Duration.ofSeconds(10)).until(() -> span("orders.check-stock", traceId).isPresent());
        SpanData check = span("orders.check-stock", traceId).orElseThrow();
        assertThat(check.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(check.getEvents()).anyMatch(e -> e.getName().equals("exception"));
    }
}
