package com.mercadoinventos.wallet.es;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * El event store: una tabla append-only en Postgres. Solo dos operaciones de verdad:
 * AGREGAR eventos al final de un stream y LEER un stream (o todos, para las proyecciones).
 * Se usa dentro de la transacción del que llama (JTA).
 */
@ApplicationScoped
public class EventStore {

    private static final String UNIQUE_VIOLATION = "23505";
    private static final String COLUMNS =
        "global_position, stream_id, version, type, schema_version, payload::text, metadata::text, occurred_at";

    @Inject
    AgroalDataSource dataSource;

    @Inject
    ObjectMapper mapper;

    /**
     * Agrega eventos al stream, SOLO si su última versión sigue siendo {@code expectedVersion}.
     * No hay SELECT ... FOR UPDATE ni candado: la restricción UNIQUE(stream_id, version) hace el trabajo.
     */
    public void append(String streamId, int expectedVersion, List<NewEvent> events) {
        String sql = "INSERT INTO events (stream_id, version, type, schema_version, payload, metadata) "
            + "VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb)";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            int version = expectedVersion;
            for (NewEvent event : events) {
                ps.setString(1, streamId);
                ps.setInt(2, ++version);
                ps.setString(3, event.type());
                ps.setInt(4, event.schemaVersion());
                ps.setString(5, event.payload().toString());
                ps.setString(6, event.metadata().toString());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            if (UNIQUE_VIOLATION.equals(e.getSQLState())) {
                throw new ConcurrencyException(streamId, expectedVersion);
            }
            throw new IllegalStateException(e);
        }
    }

    /** Los eventos del stream posteriores a {@code afterVersion}; con {@code until}, solo los ocurridos hasta ese instante. */
    public List<StoredEvent> readStream(String streamId, int afterVersion, Instant until) {
        String sql = "SELECT " + COLUMNS + " FROM events WHERE stream_id = ? AND version > ?"
            + (until != null ? " AND occurred_at <= ?" : "") + " ORDER BY version";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, streamId);
            ps.setInt(2, afterVersion);
            if (until != null) {
                ps.setTimestamp(3, Timestamp.from(until));
            }
            return readAll(ps);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Lo que ve el proyector: un evento y en qué transacción se guardó. */
    public record Positioned(String transactionId, StoredEvent event) {
    }

    /**
     * Eventos de TODOS los streams después de un checkpoint, en orden de transacción.
     *
     * <p>Por qué no alcanza con {@code global_position > checkpoint}: la secuencia se asigna al INSERTAR, pero
     * las transacciones confirman en otro orden. Si la transacción con la posición 41 confirma después que la 42,
     * un lector que ya vio la 42 se salta la 41 para siempre. Por eso se ordena por el id de transacción y solo
     * se leen las transacciones anteriores a la más vieja que sigue abierta ({@code pg_snapshot_xmin}).
     */
    public List<Positioned> readAllAfter(String transactionId, long position, int limit) {
        String sql = "SELECT transaction_id::text, " + COLUMNS + " FROM events"
            + " WHERE (transaction_id, global_position) > (?::xid8, ?)"
            + "   AND transaction_id < pg_snapshot_xmin(pg_current_snapshot())"
            + " ORDER BY transaction_id, global_position LIMIT ?";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, transactionId);
            ps.setLong(2, position);
            ps.setInt(3, limit);
            List<Positioned> result = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new Positioned(rs.getString(1), toEvent(rs, 2)));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<StoredEvent> readAll(PreparedStatement ps) throws SQLException {
        List<StoredEvent> result = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(toEvent(rs, 1));
            }
        }
        return result;
    }

    private StoredEvent toEvent(ResultSet rs, int first) throws SQLException {
        try {
            return new StoredEvent(rs.getLong(first), rs.getString(first + 1), rs.getInt(first + 2), rs.getString(first + 3),
                rs.getInt(first + 4), (ObjectNode) mapper.readTree(rs.getString(first + 5)),
                (ObjectNode) mapper.readTree(rs.getString(first + 6)), rs.getTimestamp(first + 7).toInstant());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
