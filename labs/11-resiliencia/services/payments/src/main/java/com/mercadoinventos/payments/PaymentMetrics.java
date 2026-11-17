package com.mercadoinventos.payments;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.smallrye.faulttolerance.api.CircuitBreakerMaintenance;
import io.smallrye.faulttolerance.api.CircuitBreakerState;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Métricas para ver los patrones en Grafana (Prometheus):
 *   payments_outcomes_total{outcome}        aprobados, pendientes, rechazados, fallidos
 *   payments_provider_calls_total{path}     cuántas llamadas le llegan a la pasarela, por camino
 *   payments_circuit_state                  0 = cerrado, 1 = semiabierto, 2 = abierto
 */
@ApplicationScoped
public class PaymentMetrics {

    private final LongCounter outcomes;
    private final LongCounter providerCalls;

    PaymentMetrics(Meter meter, CircuitBreakerMaintenance circuits) {
        outcomes = meter.counterBuilder("payments.outcomes").setDescription("Pagos por resultado").setUnit("1").build();
        providerCalls = meter.counterBuilder("payments.provider.calls")
            .setDescription("Llamadas a la pasarela, por camino").setUnit("1").build();
        meter.gaugeBuilder("payments.circuit.state").ofLongs()
            .setDescription("Circuito hacia la pasarela: 0 cerrado, 1 semiabierto, 2 abierto")
            .buildWithCallback(m -> m.record(switch (circuits.currentState(ResilientProvider.CIRCUIT)) {
                case CLOSED -> 0;
                case HALF_OPEN -> 1;
                case OPEN -> 2;
            }));
    }

    void outcome(String outcome) {
        outcomes.add(1, Attributes.of(AttributeKey.stringKey("outcome"), outcome));
    }

    void providerCall(String path) {
        providerCalls.add(1, Attributes.of(AttributeKey.stringKey("path"), path));
    }

    static String name(CircuitBreakerState state) {
        return state.name();
    }
}
