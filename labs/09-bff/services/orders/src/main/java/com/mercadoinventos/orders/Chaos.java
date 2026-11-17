package com.mercadoinventos.orders;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;

/** Caos: latencia inyectada en cada respuesta (PUT /admin/delay {"ms": 800}). Para comparar secuencial vs. paralelo. */
@ApplicationScoped
@Path("/admin/delay")
public class Chaos {

    public record Delay(int ms) {
    }

    private volatile int delayMs = Integer.parseInt(System.getenv().getOrDefault("DELAY_MS", "0"));

    @PUT
    public Delay set(Delay delay) {
        delayMs = Math.max(0, delay.ms());
        return new Delay(delayMs);
    }

    @GET
    public Delay get() {
        return new Delay(delayMs);
    }

    void pause() {
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

}
