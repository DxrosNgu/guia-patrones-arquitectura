package com.mercadoinventos.payments;

/** La pasarela falló (5xx). Puede ser pasajero: tiene sentido reintentar. */
public class ProviderUnavailableException extends RuntimeException {

    public ProviderUnavailableException(int status) {
        super("La pasarela respondió " + status);
    }
}
