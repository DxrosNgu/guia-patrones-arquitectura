package com.mercadoinventos.gateway;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Un servidor HTTP mínimo (del JDK) que hace dos papeles:
 *  - Keycloak: publica la clave pública en /jwks, para que el gateway verifique las firmas.
 *  - Los servicios de atrás: responde cualquier /api/** y anota qué le llegó.
 */
class FakeBackend {

    record Received(String method, String path, String authorization) {
    }

    final List<Received> received = new CopyOnWriteArrayList<>();
    private final HttpServer server;

    FakeBackend(RSAKey publicKey) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/jwks", exchange -> respond(exchange, 200, new JWKSet(publicKey).toString()));
        server.createContext("/api/", exchange -> {
            received.add(new Received(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("Authorization")));
            respond(exchange, "POST".equals(exchange.getRequestMethod()) ? 201 : 200, "{\"backend\":\"ok\"}");
        });
        server.start();
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    String url() {
        return "http://localhost:" + server.getAddress().getPort();
    }
}
