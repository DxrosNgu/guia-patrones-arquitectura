package com.mercadoinventos.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mercadoinventos.wallet.domain.Wallet;
import com.mercadoinventos.wallet.domain.WalletEvent;
import com.mercadoinventos.wallet.domain.WalletEvent.FundsDeposited;
import com.mercadoinventos.wallet.domain.WalletEvent.FundsWithdrawn;
import com.mercadoinventos.wallet.domain.WalletEvent.WalletOpened;
import com.mercadoinventos.wallet.domain.WalletEvent.WithdrawalRejected;
import com.mercadoinventos.wallet.domain.WalletException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** El agregado sin base de datos: una historia de eventos entra, un estado sale. */
class WalletTest {

    private static final List<WalletEvent> HISTORY = List.of(
        new WalletOpened("ana"),
        new FundsDeposited(new BigDecimal("100.00"), "PEN", "YAPE", null),
        new FundsWithdrawn(new BigDecimal("30.00"), "Taza"));

    @Test
    void the_balance_is_derived_from_the_history() {
        Wallet wallet = Wallet.fromHistory("w", HISTORY);

        assertThat(wallet.balance()).isEqualByComparingTo("70.00");
        assertThat(wallet.version()).isEqualTo(3);
    }

    @Test
    void a_command_decides_but_does_not_change_the_state() {
        Wallet wallet = Wallet.fromHistory("w", HISTORY);

        List<WalletEvent> decided = wallet.withdraw(new BigDecimal("20"), "Paraguas");

        assertThat(decided).containsExactly(new FundsWithdrawn(new BigDecimal("20.00"), "Paraguas"));
        assertThat(wallet.balance()).isEqualByComparingTo("70.00");
        assertThat(wallet.version()).isEqualTo(3);
    }

    @Test
    void withdrawing_more_than_the_balance_is_a_rejection_event_not_an_exception() {
        Wallet wallet = Wallet.fromHistory("w", HISTORY);

        List<WalletEvent> decided = wallet.withdraw(new BigDecimal("80"), "Lámpara");

        assertThat(decided).containsExactly(
            new WithdrawalRejected(new BigDecimal("80.00"), new BigDecimal("70.00"), "INSUFFICIENT_FUNDS"));
        wallet.apply(decided.getFirst());
        assertThat(wallet.balance()).isEqualByComparingTo("70.00");
        assertThat(wallet.version()).isEqualTo(4);
    }

    @Test
    void invalid_commands_produce_no_events() {
        Wallet wallet = Wallet.fromHistory("w", HISTORY);

        assertThatThrownBy(() -> wallet.deposit(new BigDecimal("-5"), "YAPE", null)).isInstanceOf(WalletException.class);
        assertThatThrownBy(() -> wallet.deposit(new BigDecimal("1.001"), "YAPE", null)).isInstanceOf(WalletException.class);
        assertThatThrownBy(() -> wallet.open("beto")).isInstanceOf(WalletException.class);
        assertThatThrownBy(() -> Wallet.empty("x").deposit(BigDecimal.TEN, "YAPE", null)).isInstanceOf(WalletException.class);
    }

    @Test
    void a_snapshot_plus_the_remaining_events_gives_the_same_state_as_the_full_history() {
        Wallet full = Wallet.fromHistory("w", HISTORY);
        Wallet partial = Wallet.fromHistory("w", HISTORY.subList(0, 2));

        Wallet fromSnapshot = Wallet.fromSnapshot("w", partial.version(), partial.state());
        fromSnapshot.apply(HISTORY.get(2));

        assertThat(fromSnapshot.state()).isEqualTo(full.state());
        assertThat(fromSnapshot.version()).isEqualTo(full.version());
    }
}
