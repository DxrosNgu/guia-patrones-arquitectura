package com.mercadoinventos.payments;

/** La pasarela RECHAZÓ el pago (402). Es un error de negocio: reintentarlo o esconderlo con un fallback está mal. */
public class PaymentDeclinedException extends RuntimeException {

    public PaymentDeclinedException() {
        super("La pasarela rechazó el pago");
    }
}
