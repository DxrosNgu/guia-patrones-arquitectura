package com.mercadoinventos.payments;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

/** ❌ VERSIONES INGENUAS, para comparar. */
@ApplicationScoped
public class NaiveProvider {

    private static final Logger LOG = Logger.getLogger(NaiveProvider.class);

    @Inject
    @RestClient
    ProviderClient provider;

    @Inject
    PaymentMetrics metrics;

    /** Sin timeout (solo el del cliente HTTP, 30 s), sin reintentos, sin fallback. */
    public ChargeResult unprotected(ChargeRequest request) {
        metrics.providerCall("naive");
        return provider.charge(request);
    }

    /** Reintenta 5 veces, al instante, ante cualquier error. Multiplica la carga sobre una pasarela que ya está mal. */
    public ChargeResult retryStorm(ChargeRequest request) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 6; attempt++) {
            try {
                metrics.providerCall("naive-retry");
                return provider.charge(request);
            } catch (RuntimeException e) {
                last = e;
                LOG.warnf("Intento %d falló: %s. Reintento YA.", attempt, e.getMessage());
            }
        }
        throw last;
    }
}
