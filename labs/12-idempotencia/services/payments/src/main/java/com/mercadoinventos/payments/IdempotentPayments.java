package com.mercadoinventos.payments;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Patrón Idempotency Key (como en la API de Stripe).
 *
 * El cliente genera una clave única por INTENCIÓN de pago (no por intento) y la
 * manda en el header Idempotency-Key. Si reintenta (timeout, doble clic, red
 * caída), manda la MISMA clave, y aquí:
 *
 *   - clave nueva                    → se cobra y se guarda la respuesta
 *   - clave conocida, mismo cuerpo   → NO se cobra; se devuelve la respuesta guardada
 *   - clave conocida, otro cuerpo    → 422: es un error del cliente
 *
 * Todo ocurre en UNA transacción: el INSERT de la clave, el cobro y la respuesta.
 */
@ApplicationScoped
public class IdempotentPayments {

    private static final Logger LOG = Logger.getLogger(IdempotentPayments.class);

    public record Result(int status, String body, boolean replayed) {
    }

    private final EntityManager em;
    private final PaymentService payments;
    private final ObjectMapper json;

    IdempotentPayments(EntityManager em, PaymentService payments, ObjectMapper json) {
        this.em = em;
        this.payments = payments;
        this.json = json;
    }

    @Transactional
    public Result charge(String key, PaymentResource.PaymentRequest request) {
        String hash = sha256(toJson(request));

        // Si otra transacción ya insertó esta clave y no terminó, este INSERT ESPERA.
        // Cuando la otra confirma, "DO NOTHING" → inserted = 0 y leemos su respuesta.
        int inserted = em.createNativeQuery(
                "INSERT INTO idempotency_keys (key, request_hash) VALUES (?1, ?2) ON CONFLICT (key) DO NOTHING")
            .setParameter(1, key).setParameter(2, hash)
            .executeUpdate();

        if (inserted == 0) {
            Object[] stored = (Object[]) em.createNativeQuery(
                    "SELECT request_hash, response_status, response_body FROM idempotency_keys WHERE key = ?1")
                .setParameter(1, key).getSingleResult();
            if (!hash.equals(stored[0])) {
                return new Result(422, "{\"detail\":\"Esta Idempotency-Key ya se usó con otra petición\"}", false);
            }
            LOG.infof("↩️  Reintento con Idempotency-Key %s: se devuelve la respuesta guardada, SIN cobrar", key);
            return new Result(((Number) stored[1]).intValue(), (String) stored[2], true);
        }

        Payment payment = payments.charge(request);
        String body = toJson(payment);
        em.createNativeQuery("UPDATE idempotency_keys SET response_status = 201, response_body = ?2 WHERE key = ?1")
            .setParameter(1, key).setParameter(2, body)
            .executeUpdate();
        return new Result(201, body, false);
    }

    private String toJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
