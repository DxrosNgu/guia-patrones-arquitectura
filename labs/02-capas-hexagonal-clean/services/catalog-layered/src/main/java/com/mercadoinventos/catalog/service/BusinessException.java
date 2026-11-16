package com.mercadoinventos.catalog.service;

public class BusinessException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    public BusinessException(String code, int httpStatus, String message) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String code() { return code; }

    // ⚠️ La capa de negocio decide el código HTTP: otra fuga de la capa web hacia el negocio.
    public int httpStatus() { return httpStatus; }
}
