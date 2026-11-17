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
import java.util.Optional;

/** Snapshots: la foto del estado en una versión. Es una caché de la reconstrucción, no una fuente de verdad. */
@ApplicationScoped
public class SnapshotStore {

    public record Snapshot(int version, ObjectNode state) {
    }

    @Inject
    AgroalDataSource dataSource;

    @Inject
    ObjectMapper mapper;

    /** El snapshot del stream, si existe y tiene el formato que este código entiende. */
    public Optional<Snapshot> find(String streamId, int snapshotSchema) {
        String sql = "SELECT version, state::text FROM snapshots WHERE stream_id = ? AND snapshot_schema = ?";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, streamId);
            ps.setInt(2, snapshotSchema);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(new Snapshot(rs.getInt(1), (ObjectNode) mapper.readTree(rs.getString(2))))
                    : Optional.empty();
            }
        } catch (SQLException | JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public void save(String streamId, int version, int snapshotSchema, ObjectNode state) {
        String sql = "INSERT INTO snapshots (stream_id, version, snapshot_schema, state) VALUES (?, ?, ?, ?::jsonb) "
            + "ON CONFLICT (stream_id) DO UPDATE SET version = excluded.version, snapshot_schema = excluded.snapshot_schema, "
            + "state = excluded.state, taken_at = now() "
            + "WHERE snapshots.version < excluded.version OR snapshots.snapshot_schema <> excluded.snapshot_schema";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, streamId);
            ps.setInt(2, version);
            ps.setInt(3, snapshotSchema);
            ps.setString(4, state.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
