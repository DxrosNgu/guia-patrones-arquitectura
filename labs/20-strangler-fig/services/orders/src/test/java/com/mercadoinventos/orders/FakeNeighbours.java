package com.mercadoinventos.orders;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Un servidor del JDK que hace de catálogo nuevo y de legado. Anota los pedidos que el legado recibe
 * y los devuelve en GET /received (el test los lee por HTTP: comparte la red, no la memoria).
 */
public class FakeNeighbours implements QuarkusTestResourceLifecycleManager {

    private HttpServer server;
    private final List<String> received = new CopyOnWriteArrayList<>();

    @Override
    public Map<String, String> start() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/api/catalog/products/", exchange -> {
            String sku = exchange.getRequestURI().getPath().substring("/api/catalog/products/".length());
            if (sku.equals("INV-001")) {
                respond(exchange, 200, "{\"sku\":\"INV-001\",\"name\":\"Paraguas solar\",\"price\":49.90}");
            } else {
                respond(exchange, 404, "{\"message\":\"No existe\"}");
            }
        });
        server.createContext("/internal/orders", exchange -> {
            if ("PUT".equals(exchange.getRequestMethod())) {
                received.add(exchange.getRequestURI().getPath().substring("/internal/orders/".length()));
                respond(exchange, 204, null);
            } else {
                respond(exchange, 200, "[{\"id\":1001,\"customer\":\"ana\",\"sku\":\"INV-001\",\"quantity\":2,\"total\":99.8,"
                    + "\"createdAt\":\"2026-10-01T15:00:00+00:00\"},{\"id\":1002,\"customer\":\"ana\",\"sku\":\"INV-004\","
                    + "\"quantity\":1,\"total\":25.5,\"createdAt\":\"2026-10-02T15:00:00.123456+00:00\"}]");
            }
        });
        server.createContext("/received", exchange -> respond(exchange, 200, "[\"" + String.join("\",\"", received) + "\"]"));
        server.start();
        String url = "http://localhost:" + server.getAddress().getPort();
        return Map.of("quarkus.rest-client.catalog.url", url, "quarkus.rest-client.legacy.url", url, "fake.url", url);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        if (body == null) {
            exchange.sendResponseHeaders(status, -1);
        } else {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }

    @Override
    public void stop() {
        server.stop(0);
    }
}
