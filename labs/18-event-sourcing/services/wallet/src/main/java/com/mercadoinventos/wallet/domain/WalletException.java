package com.mercadoinventos.wallet.domain;

/** Un comando inválido: no produce ningún evento. */
public class WalletException extends RuntimeException {

    public WalletException(String message) {
        super(message);
    }
}
