package com.mercadoinventos.inventory;

/** Falla que puede resolverse sola (otro servicio caído). Se reintenta. */
public class TransientException extends RuntimeException {

    public TransientException(String message) {
        super(message);
    }
}
