package com.mercadoinventos.gateway;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/** Un servidor HTTP mínimo (del JDK) que responde cualquier /api/** diciendo quién es. */
class FakeBackend {

    private final HttpServer server;

    FakeBackend(String name) {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/api/", exchange -> {
            byte[] body = ("{\"servedBy\":\"" + name + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    String url() {
        return "http://localhost:" + server.getAddress().getPort();
    }
}
