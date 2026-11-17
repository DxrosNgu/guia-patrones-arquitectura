package com.mercadoinventos.wallet.domain;

import com.mercadoinventos.wallet.domain.WalletEvent.FundsDeposited;
import com.mercadoinventos.wallet.domain.WalletEvent.FundsWithdrawn;
import com.mercadoinventos.wallet.domain.WalletEvent.WalletOpened;
import com.mercadoinventos.wallet.domain.WalletEvent.WithdrawalRejected;
import java.math.BigDecimal;
import java.util.List;

/**
 * El AGREGADO monedero, con Event Sourcing. Dos tipos de métodos, y no se mezclan:
 *
 * <ul>
 *   <li><b>Comandos</b> ({@link #deposit}, {@link #withdraw}...): validan contra el estado actual y DEVUELVEN
 *       los eventos que ocurrirían. No cambian nada.</li>
 *   <li><b>{@link #apply}</b>: cambia el estado a partir de un evento. No valida nada: el evento ya ocurrió.
 *       Se usa igual al reconstruir desde la historia y después de guardar un evento nuevo.</li>
 * </ul>
 */
public class Wallet {

    /** El estado que guarda un snapshot. Si cambia su forma, se sube la versión y los snapshots viejos se ignoran. */
    public record State(String owner, BigDecimal balance, boolean opened) {
        public static final int SCHEMA = 1;
    }

    private final String id;
    private String owner;
    private BigDecimal balance = BigDecimal.ZERO.setScale(2);
    private boolean opened;
    private int version;

    private Wallet(String id) {
        this.id = id;
    }

    public static Wallet empty(String id) {
        return new Wallet(id);
    }

    public static Wallet fromSnapshot(String id, int version, State state) {
        Wallet wallet = new Wallet(id);
        wallet.owner = state.owner();
        wallet.balance = state.balance();
        wallet.opened = state.opened();
        wallet.version = version;
        return wallet;
    }

    public static Wallet fromHistory(String id, List<WalletEvent> history) {
        Wallet wallet = new Wallet(id);
        history.forEach(wallet::apply);
        return wallet;
    }

    // ── Comandos: deciden ────────────────────────────────────────────────

    public List<WalletEvent> open(String newOwner) {
        if (opened) {
            throw new WalletException("El monedero ya existe");
        }
        if (newOwner == null || newOwner.isBlank()) {
            throw new WalletException("Falta el dueño");
        }
        return List.of(new WalletOpened(newOwner));
    }

    public List<WalletEvent> deposit(BigDecimal amount, String channel, String description) {
        requireOpened();
        requirePositive(amount);
        return List.of(new FundsDeposited(amount.setScale(2), "PEN", channel == null ? "DESCONOCIDO" : channel, description));
    }

    public List<WalletEvent> withdraw(BigDecimal amount, String description) {
        requireOpened();
        requirePositive(amount);
        if (balance.compareTo(amount) < 0) {
            return List.of(new WithdrawalRejected(amount.setScale(2), balance, "INSUFFICIENT_FUNDS"));
        }
        return List.of(new FundsWithdrawn(amount.setScale(2), description));
    }

    // ── Evolución: aplica ────────────────────────────────────────────────

    public void apply(WalletEvent event) {
        switch (event) {
            case WalletOpened e -> {
                owner = e.owner();
                opened = true;
            }
            case FundsDeposited e -> balance = balance.add(e.amount());
            case FundsWithdrawn e -> balance = balance.subtract(e.amount());
            case WithdrawalRejected e -> {
                // No cambia el saldo, pero es parte de la historia: la versión avanza igual.
            }
        }
        version++;
    }

    private void requireOpened() {
        if (!opened) {
            throw new WalletException("El monedero no existe");
        }
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2) {
            throw new WalletException("El monto debe ser positivo y con 2 decimales como máximo");
        }
    }

    public State state() {
        return new State(owner, balance, opened);
    }

    public String id() {
        return id;
    }

    public String owner() {
        return owner;
    }

    public BigDecimal balance() {
        return balance;
    }

    public boolean exists() {
        return opened;
    }

    public int version() {
        return version;
    }
}
