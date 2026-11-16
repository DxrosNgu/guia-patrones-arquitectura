package com.mercadoinventos.inventory;

/** El mensaje no se puede procesar NUNCA (mal formado). No se reintenta: va a la DLQ. */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message);
    }
}
