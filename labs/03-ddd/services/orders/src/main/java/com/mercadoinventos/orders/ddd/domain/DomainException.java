package com.mercadoinventos.orders.ddd.domain;

/** Se rompió una invariante. El código ({@code MAX_QUANTITY_EXCEEDED}...) viaja hasta la respuesta HTTP. */
public class DomainException extends RuntimeException {

    private final String code;

    public DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
