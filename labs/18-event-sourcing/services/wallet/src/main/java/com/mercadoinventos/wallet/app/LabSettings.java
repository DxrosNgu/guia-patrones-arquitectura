package com.mercadoinventos.wallet.app;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Perillas del lab que se cambian en caliente (PUT /admin/...). Se leen con métodos: ver lab 13 (proxies de CDI). */
@ApplicationScoped
public class LabSettings {

    private volatile int thinkTimeMs;
    private volatile int maxAttempts;

    LabSettings(@ConfigProperty(name = "wallet.think-time-ms") int thinkTimeMs,
                @ConfigProperty(name = "wallet.max-attempts") int maxAttempts) {
        this.thinkTimeMs = thinkTimeMs;
        this.maxAttempts = maxAttempts;
    }

    public int thinkTimeMs() {
        return thinkTimeMs;
    }

    public void setThinkTimeMs(int ms) {
        thinkTimeMs = Math.max(0, ms);
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int attempts) {
        maxAttempts = Math.max(1, attempts);
    }

    /** Simula el tiempo que tarda un comando entre LEER el estado y GUARDAR su decisión. */
    public void think() {
        if (thinkTimeMs > 0) {
            try {
                Thread.sleep(thinkTimeMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
