package com.mercadoinventos.payments;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.faulttolerance.api.CircuitBreakerMaintenance;
import io.smallrye.faulttolerance.api.CircuitBreakerState;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.jboss.logging.Logger;

/** Los pagos (en memoria) y el reconciliador que confirma los pendientes. */
@ApplicationScoped
public class PaymentService {

    private static final Logger LOG = Logger.getLogger(PaymentService.class);

    private final Map<String, Payment> payments = new ConcurrentHashMap<>();

    @Inject
    ResilientProvider resilient;

    @Inject
    PaymentMetrics metrics;

    @Inject
    CircuitBreakerMaintenance circuits;

    void logCircuitChanges(@Observes StartupEvent event) {
        circuits.onStateChange(ResilientProvider.CIRCUIT,
            state -> LOG.warnf("⚡ Circuito %s → %s", ResilientProvider.CIRCUIT, state));
    }

    /** Siempre la misma clave por pedido: reintentos y reconciliación nunca cobran dos veces. */
    static ChargeRequest requestFor(String orderId, BigDecimal amount) {
        return new ChargeRequest("pay-" + orderId, orderId, amount);
    }

    public Payment pay(String orderId, BigDecimal amount, Function<ChargeRequest, ChargeResult> provider) {
        try {
            ChargeResult result = provider.apply(requestFor(orderId, amount));
            Payment payment = new Payment(orderId, amount, result.status(), result.chargeId(), Instant.now());
            payments.put(orderId, payment);
            metrics.outcome(result.status().toLowerCase());
            return payment;
        } catch (PaymentDeclinedException e) {
            Payment declined = new Payment(orderId, amount, "DECLINED", null, Instant.now());
            payments.put(orderId, declined);
            metrics.outcome("declined");
            throw e;
        } catch (RuntimeException e) {
            metrics.outcome("failed");
            throw e;
        }
    }

    public Optional<Payment> find(String orderId) {
        return Optional.ofNullable(payments.get(orderId));
    }

    public List<Payment> pending() {
        return payments.values().stream().filter(p -> p.status().equals("PENDING_CONFIRMATION")).toList();
    }

    /**
     * El fallback dejó pagos "pendientes de confirmación". Cada pocos segundos se reintentan
     * con la MISMA idempotencyKey: si la pasarela ya los había cobrado, devuelve el mismo cobro.
     * Con el circuito abierto ni lo intenta: sería pedirle a la pasarela que se caiga más.
     */
    @Scheduled(every = "${payments.reconcile-every:5s}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void reconcile() {
        if (circuits.currentState(ResilientProvider.CIRCUIT) == CircuitBreakerState.OPEN) {
            return;
        }
        for (Payment payment : pending()) {
            ChargeResult result = resilient.charge(requestFor(payment.orderId(), payment.amount()));
            if ("APPROVED".equals(result.status())) {
                payments.put(payment.orderId(), payment.with("APPROVED", result.chargeId()));
                metrics.outcome("reconciled");
                LOG.infof("✅ Pago %s confirmado por el reconciliador (%s)", payment.orderId(), result.chargeId());
            }
        }
    }
}
