package com.mercadoinventos.payments;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import org.jboss.logging.Logger;

/** Control de caos: hacer que pagos tarde N ms en procesar cada comando (caso S6). */
@ApplicationScoped
@Path("/api/chaos")
public class Chaos {

    private static final Logger LOG = Logger.getLogger(Chaos.class);

    public record Settings(long delayMs) {
    }

    private volatile long delayMs;

    @GET
    public Settings get() {
        return new Settings(delayMs);
    }

    @PUT
    public Settings set(Settings settings) {
        delayMs = Math.max(0, settings.delayMs());
        LOG.warnf("🎛️  Pagos responderá con %d ms de demora", delayMs);
        return get();
    }

    void maybeDelay() {
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
