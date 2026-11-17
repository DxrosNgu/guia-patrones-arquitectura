package com.mercadoinventos.wallet.domain;

import java.math.BigDecimal;

/**
 * Los hechos del monedero, en pasado: ya ocurrieron y no se discuten.
 * Su forma es la ÚLTIMA versión de cada evento; los formatos viejos los traduce el Upcaster al leer.
 */
public sealed interface WalletEvent {

    record WalletOpened(String owner) implements WalletEvent {
    }

    record FundsDeposited(BigDecimal amount, String currency, String channel, String description) implements WalletEvent {
    }

    record FundsWithdrawn(BigDecimal amount, String description) implements WalletEvent {
    }

    /** Un retiro rechazado también es un hecho: queda en la historia (y en la proyección mensual). */
    record WithdrawalRejected(BigDecimal amount, BigDecimal balance, String reason) implements WalletEvent {
    }
}
