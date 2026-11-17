package com.mercadoinventos.payments;

import io.smallrye.faulttolerance.api.CircuitBreakerName;
import io.smallrye.faulttolerance.api.ExponentialBackoff;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.faulttolerance.exceptions.BulkheadException;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * La llamada a la pasarela, PROTEGIDA. Los cinco patrones son anotaciones y se aplican
 * en este orden, de afuera hacia adentro (lo define la especificación de MicroProfile):
 *
 *   Fallback( Retry( CircuitBreaker( Timeout( Bulkhead( llamada ) ) ) ) )
 *
 * Así, CADA intento de Retry pasa por el circuit breaker (que lo cuenta) y tiene su propio timeout.
 */
@ApplicationScoped
public class ResilientProvider {

    public static final String CIRCUIT = "payment-provider";

    @Inject
    @RestClient
    ProviderClient provider;

    @Inject
    PaymentMetrics metrics;

    @Fallback(fallbackMethod = "pendingConfirmation",
              skipOn = PaymentDeclinedException.class)                 // un rechazo NO se esconde
    @Retry(maxRetries = 2, delay = 100, jitter = 50,
           abortOn = {PaymentDeclinedException.class,                  // reintentar un rechazo no sirve
                      CircuitBreakerOpenException.class,               // si el circuito está abierto, no insistir
                      BulkheadException.class})                         // ni cuando no hay lugar
    @ExponentialBackoff(factor = 2, maxDelay = 1000)                    // 100 ms, 200 ms, 400 ms... (+ jitter)
    @CircuitBreaker(requestVolumeThreshold = 6, failureRatio = 0.5,
                    delay = 5000, successThreshold = 2,
                    skipOn = {PaymentDeclinedException.class,           // un rechazo no es una falla de la pasarela
                              BulkheadException.class})                 // ni que NOSOTROS estemos saturados (ver README)
    @CircuitBreakerName(CIRCUIT)
    @Timeout(500)                                                        // ms por intento
    @Bulkhead(5)                                                         // máximo 5 llamadas a la vez a la pasarela
    public ChargeResult charge(ChargeRequest request) {
        metrics.providerCall("resilient");
        return provider.charge(request);
    }

    /** Fallback: no sabemos si se cobró. El pago queda pendiente y el reconciliador lo confirma después. */
    ChargeResult pendingConfirmation(ChargeRequest request) {
        return ChargeResult.pendingConfirmation();
    }
}
