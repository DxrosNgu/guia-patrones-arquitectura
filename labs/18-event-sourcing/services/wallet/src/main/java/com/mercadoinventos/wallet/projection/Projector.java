package com.mercadoinventos.wallet.projection;

import com.mercadoinventos.wallet.app.EventCodec;
import com.mercadoinventos.wallet.domain.WalletEvent;
import com.mercadoinventos.wallet.domain.WalletEvent.FundsDeposited;
import com.mercadoinventos.wallet.domain.WalletEvent.FundsWithdrawn;
import com.mercadoinventos.wallet.domain.WalletEvent.WalletOpened;
import com.mercadoinventos.wallet.domain.WalletEvent.WithdrawalRejected;
import com.mercadoinventos.wallet.es.EventStore;
import com.mercadoinventos.wallet.es.EventStore.Positioned;
import com.mercadoinventos.wallet.es.StoredEvent;
import io.agroal.api.AgroalDataSource;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * El PROYECTOR: lee los eventos nuevos de todos los monederos y mantiene dos vistas para consultar
 * (saldo actual y movimientos por mes).
 *
 * <p>Las vistas y el checkpoint están en la MISMA base que el event store, así que se actualizan en
 * UNA transacción: o se aplica el lote y avanza el checkpoint, o nada. No hace falta deduplicar por
 * eventId (como en el lab 14, donde la vista y Kafka eran sistemas distintos).
 */
@ApplicationScoped
public class Projector {

    private static final Logger LOG = Logger.getLogger(Projector.class);
    private static final String NAME = "wallet-views";
    private static final int BATCH = 500;
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    @Inject
    EventStore store;

    @Inject
    EventCodec codec;

    @Inject
    AgroalDataSource dataSource;

    @ConfigProperty(name = "wallet.zone")
    ZoneId zone;

    @Scheduled(every = "${wallet.projector.every}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void poll() {
        int applied;
        do {
            applied = QuarkusTransaction.requiringNew().call(this::step);
        } while (applied == BATCH);
    }

    /** Aplica un lote y avanza el checkpoint, en una transacción. Devuelve cuántos eventos aplicó. */
    int step() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            String[] checkpoint = checkpoint(c);
            List<Positioned> batch = store.readAllAfter(checkpoint[0], Long.parseLong(checkpoint[1]), BATCH);
            for (Positioned p : batch) {
                apply(c, p.event(), codec.decode(p.event()));
            }
            if (!batch.isEmpty()) {
                Positioned last = batch.getLast();
                try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE projection_checkpoints SET transaction_id = ?::xid8, global_position = ? WHERE name = ?")) {
                    ps.setString(1, last.transactionId());
                    ps.setLong(2, last.event().position());
                    ps.setString(3, NAME);
                    ps.executeUpdate();
                }
                LOG.debugf("Proyectados %d eventos (hasta la posición %d)", batch.size(), last.event().position());
            }
            return batch.size();
        }
    }

    private void apply(Connection c, StoredEvent stored, WalletEvent event) throws SQLException {
        String wallet = stored.streamId();
        String month = stored.occurredAt().atZone(zone).format(MONTH);
        Timestamp at = Timestamp.from(stored.occurredAt());
        switch (event) {
            case WalletOpened e -> update(c, "INSERT INTO balance_view VALUES (?, ?, 0, 0, ?)", wallet, e.owner(), at);
            case FundsDeposited e -> {
                update(c, "UPDATE balance_view SET balance = balance + ?, movements = movements + 1, updated_at = ? "
                    + "WHERE wallet_id = ?", e.amount(), at, wallet);
                update(c, "INSERT INTO monthly_movements (wallet_id, month, deposits) VALUES (?, ?, ?) ON CONFLICT "
                    + "(wallet_id, month) DO UPDATE SET deposits = monthly_movements.deposits + excluded.deposits",
                    wallet, month, e.amount());
            }
            case FundsWithdrawn e -> {
                update(c, "UPDATE balance_view SET balance = balance - ?, movements = movements + 1, updated_at = ? "
                    + "WHERE wallet_id = ?", e.amount(), at, wallet);
                update(c, "INSERT INTO monthly_movements (wallet_id, month, withdrawals) VALUES (?, ?, ?) ON CONFLICT "
                    + "(wallet_id, month) DO UPDATE SET withdrawals = monthly_movements.withdrawals + excluded.withdrawals",
                    wallet, month, e.amount());
            }
            case WithdrawalRejected e -> update(c, "INSERT INTO monthly_movements (wallet_id, month, rejected) "
                + "VALUES (?, ?, 1) ON CONFLICT (wallet_id, month) DO UPDATE SET rejected = monthly_movements.rejected + 1",
                wallet, month);
        }
    }

    /** Borra las vistas y el checkpoint: el próximo poll las rearma desde el primer evento. */
    public void rebuild() {
        QuarkusTransaction.requiringNew().run(() -> {
            try (Connection c = dataSource.getConnection()) {
                update(c, "TRUNCATE balance_view, monthly_movements");
                update(c, "UPDATE projection_checkpoints SET transaction_id = '0', global_position = 0 WHERE name = ?", NAME);
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        });
        LOG.info("Vistas borradas: se reconstruyen desde el primer evento");
    }

    /** Posición del checkpoint y cuántos eventos faltan proyectar. */
    public long[] status() {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT p.global_position, (SELECT count(*) FROM events e "
                 + "WHERE (e.transaction_id, e.global_position) > (p.transaction_id, p.global_position)) "
                 + "FROM projection_checkpoints p WHERE p.name = ?")) {
            ps.setString(1, NAME);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new long[] {rs.getLong(1), rs.getLong(2)};
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private String[] checkpoint(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
            "SELECT transaction_id::text, global_position FROM projection_checkpoints WHERE name = ?")) {
            ps.setString(1, NAME);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new String[] {rs.getString(1), String.valueOf(rs.getLong(2))};
            }
        }
    }

    private static void update(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                if (params[i] instanceof BigDecimal b) {
                    ps.setBigDecimal(i + 1, b);
                } else {
                    ps.setObject(i + 1, params[i]);
                }
            }
            ps.executeUpdate();
        }
    }
}
