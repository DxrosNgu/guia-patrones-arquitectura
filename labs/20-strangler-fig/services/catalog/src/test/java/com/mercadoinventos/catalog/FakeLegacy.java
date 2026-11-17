package com.mercadoinventos.catalog;

import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** El legado, reducido a lo que el catálogo nuevo le pide: la lista de productos. */
public class FakeLegacy implements QuarkusTestResourceLifecycleManager {

    private HttpServer server;

    @Override
    public Map<String, String> start() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/api/catalog/products", exchange -> {
            byte[] body = "[{\"sku\":\"INV-001\",\"name\":\"Paraguas solar\",\"price\":49.9},{\"sku\":\"INV-004\",\"name\":\"Taza que avisa cuando se enfría\",\"price\":25.5}]"
                .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return Map.of("quarkus.rest-client.legacy.url", "http://localhost:" + server.getAddress().getPort());
    }

    @Override
    public void stop() {
        server.stop(0);
    }
}
