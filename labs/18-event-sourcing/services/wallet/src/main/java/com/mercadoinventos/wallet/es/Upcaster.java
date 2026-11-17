package com.mercadoinventos.wallet.es;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.Map;

/**
 * UPCASTING: los eventos viejos NO se reescriben (el event store es append-only). Se convierten al formato
 * actual cada vez que se leen. El resto del código solo conoce la última versión de cada evento.
 *
 * <p>Regla: cuando cambia el formato de un evento, se sube su versión en {@link #CURRENT} y se agrega un paso
 * "vN → vN+1" aquí. Los pasos se encadenan: un evento v1 pasa por v2, v3... hasta la actual.
 */
public final class Upcaster {

    /** La versión actual del formato de cada tipo de evento. */
    public static final Map<String, Integer> CURRENT = Map.of(
        "WalletOpened", 1,
        "FundsDeposited", 2,
        "FundsWithdrawn", 1,
        "WithdrawalRejected", 1);

    private Upcaster() {
    }

    public static StoredEvent upcast(StoredEvent event) {
        Integer current = CURRENT.get(event.type());
        if (current == null) {
            throw new IllegalStateException("Tipo de evento desconocido: " + event.type());
        }
        StoredEvent result = event;
        while (result.schemaVersion() < current) {
            result = step(result);
        }
        return result;
    }

    private static StoredEvent step(StoredEvent event) {
        return switch (event.type() + "/v" + event.schemaVersion()) {
            case "FundsDeposited/v1" -> fundsDepositedV1ToV2(event);
            default -> throw new IllegalStateException(
                "No hay upcaster para " + event.type() + " v" + event.schemaVersion());
        };
    }

    /**
     * v1 (hasta octubre de 2026): {"amountCents": 50000}, en céntimos y siempre en soles.
     * v2: {"amount": 500.00, "currency": "PEN", "channel": "YAPE", "description": "..."}.
     * Lo que v1 no sabía (el canal) no se inventa: queda "DESCONOCIDO".
     */
    private static StoredEvent fundsDepositedV1ToV2(StoredEvent event) {
        // withExactBigDecimals: sin esto, 500.00 se guardaría como 5E+2.
        ObjectNode v2 = JsonNodeFactory.withExactBigDecimals(true).objectNode();
        v2.put("amount", BigDecimal.valueOf(event.payload().get("amountCents").asLong(), 2));
        v2.put("currency", "PEN");
        v2.put("channel", "DESCONOCIDO");
        v2.putNull("description");
        return event.withPayload(2, v2);
    }
}
