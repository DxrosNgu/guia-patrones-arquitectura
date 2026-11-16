package com.mercadoinventos.orders;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * El estado PERSISTIDO de una saga. Si el orquestador se cae, al volver lee esta
 * tabla y sabe exactamente en qué paso estaba cada saga (caso S10).
 */
@Entity
@Table(name = "saga_instance")
public class SagaInstance extends PanacheEntityBase {

    public enum State {
        RESERVING_STOCK(false), CHARGING(false), SCHEDULING_SHIPMENT(false), COMPENSATING(false),
        APPROVED(true), REJECTED(true), CANCELLED(true), EXPIRED(true);

        final boolean finished;

        State(boolean finished) {
            this.finished = finished;
        }
    }

    @Id
    public UUID orderId;
    @Enumerated(EnumType.STRING)
    public State state;
    /** A qué estado final se llega cuando terminan las compensaciones. */
    @Enumerated(EnumType.STRING)
    public State outcome;
    /** Compensaciones pedidas que todavía no respondieron: "ReleaseStock,RefundPayment". */
    public String pendingCompensations;
    public int attempts;
    /** Hasta cuándo se espera la respuesta del paso actual. */
    public Instant deadline;
    public Instant updatedAt;

    List<String> pending() {
        return pendingCompensations == null || pendingCompensations.isBlank()
            ? new ArrayList<>()
            : new ArrayList<>(Arrays.asList(pendingCompensations.split(",")));
    }

    void pending(List<String> compensations) {
        pendingCompensations = String.join(",", compensations);
    }
}
