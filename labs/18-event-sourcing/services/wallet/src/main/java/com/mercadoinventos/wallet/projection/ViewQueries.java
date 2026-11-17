package com.mercadoinventos.wallet.projection;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Consultas sobre las PROYECCIONES (tablas normales): rápidas, pero pueden ir un poco atrasadas. */
@ApplicationScoped
public class ViewQueries {

    @Inject
    AgroalDataSource dataSource;

    public List<Map<String, Object>> balances() {
        return query("SELECT wallet_id AS \"walletId\", owner, balance, movements, updated_at AS \"updatedAt\" "
            + "FROM balance_view ORDER BY balance DESC, wallet_id");
    }

    public List<Map<String, Object>> monthly(String id) {
        return query("SELECT month, deposits, withdrawals, rejected FROM monthly_movements WHERE wallet_id = ? ORDER BY month", id);
    }

    private List<Map<String, Object>> query(String sql, Object... params) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int col = 1; col <= meta.getColumnCount(); col++) {
                        Object value = rs.getObject(col);
                        row.put(meta.getColumnLabel(col), value instanceof java.sql.Timestamp t ? t.toInstant().toString() : value);
                    }
                    rows.add(row);
                }
            }
            return rows;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
