package com.mercadoinventos.wallet.es;

/** Otro comando guardó en el mismo stream entre que este leyó y quiso guardar. */
public class ConcurrencyException extends RuntimeException {

    public ConcurrencyException(String streamId, int expectedVersion) {
        super("Conflicto de versión en " + streamId + ": se esperaba la versión " + expectedVersion
            + " pero otro comando ya guardó la " + (expectedVersion + 1));
    }
}
