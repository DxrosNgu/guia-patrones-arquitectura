package com.mercadoinventos.payments;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Una pasarela falsa con los MISMOS controles que la simulada del lab (vía HTTP):
 *   PUT /admin/chaos {"down":true,"latencyMs":800}   GET /admin/stats   POST /admin/reset
 */
public class FakeProvider implements QuarkusTestResourceLifecycleManager {

    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();
    private final Set<String> charges = ConcurrentHashMap.newKeySet();
    private volatile boolean down;
    private volatile int latencyMs;

    @Override
    public Map<String, String> start() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.setExecutor(Executors.newFixedThreadPool(20));
        server.createContext("/charges", this::charge);
        server.createContext("/admin/chaos", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            down = body.contains("\"down\":true");
            Matcher m = Pattern.compile("\"latencyMs\":(\\d+)").matcher(body);
            latencyMs = m.find() ? Integer.parseInt(m.group(1)) : 0;
            respond(exchange, 200, "{}");
        });
        server.createContext("/admin/stats", exchange ->
            respond(exchange, 200, "{\"requests\":" + requests.get() + ",\"charges\":" + charges.size() + "}"));
        server.createContext("/admin/reset", exchange -> {
            requests.set(0);
            charges.clear();
            down = false;
            latencyMs = 0;
            respond(exchange, 200, "{}");
        });
        server.start();
        return Map.of("quarkus.rest-client.provider.url", "http://localhost:" + server.getAddress().getPort());
    }

    private void charge(HttpExchange exchange) throws IOException {
        requests.incrementAndGet();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (down) {
            respond(exchange, 503, "{\"error\":\"down\"}");
            return;
        }
        if (latencyMs > 0) {
            try {
                Thread.sleep(latencyMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        Matcher amount = Pattern.compile("\"amount\":([0-9.]+)").matcher(body);
        if (amount.find() && Double.parseDouble(amount.group(1)) > 1000) {
            respond(exchange, 402, "{\"status\":\"DECLINED\"}");
            return;
        }
        Matcher key = Pattern.compile("\"idempotencyKey\":\"([^\"]+)\"").matcher(body);
        String k = key.find() ? key.group(1) : "?";
        boolean replayed = !charges.add(k);
        respond(exchange, 201, "{\"chargeId\":\"ch_" + k + "\",\"status\":\"APPROVED\",\"replayed\":" + replayed + "}");
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Override
    public void stop() {
        server.stop(0);
    }
}
