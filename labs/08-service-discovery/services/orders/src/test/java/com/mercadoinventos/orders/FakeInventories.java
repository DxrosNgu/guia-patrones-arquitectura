package com.mercadoinventos.orders;

import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Dos "instancias" de inventario (servidores del JDK) que dicen su nombre: inventory-a e inventory-b. */
public class FakeInventories implements QuarkusTestResourceLifecycleManager {

    private List<HttpServer> servers;

    @Override
    public Map<String, String> start() {
        servers = List.of(server("inventory-a"), server("inventory-b"));
        String a = "localhost:" + servers.get(0).getAddress().getPort();
        String b = "localhost:" + servers.get(1).getAddress().getPort();
        return Map.of(
            // Por DNS: un solo nombre → una sola dirección (aquí, la instancia a).
            "quarkus.rest-client.inventory-dns.url", "http://" + a,
            // Por Stork: en los tests, una lista fija en lugar de Consul.
            "quarkus.stork.inventory.service-discovery.type", "static",
            "quarkus.stork.inventory.service-discovery.address-list", a + "," + b);
    }

    private static HttpServer server(String name) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/api/stock/", exchange -> {
                String sku = exchange.getRequestURI().getPath().substring("/api/stock/".length());
                byte[] body = ("{\"sku\":\"" + sku + "\",\"available\":12,\"instance\":\"" + name + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void stop() {
        servers.forEach(s -> s.stop(0));
    }
}
