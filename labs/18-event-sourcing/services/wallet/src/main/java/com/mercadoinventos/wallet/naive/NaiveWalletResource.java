package com.mercadoinventos.wallet.naive;

import com.mercadoinventos.wallet.app.LabSettings;
import io.agroal.api.AgroalDataSource;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

/**
 * La versión INGENUA: una fila con el saldo actual. Leer, decidir y escribir el saldo nuevo.
 * Dos retiros a la vez leen el mismo saldo y el segundo PISA al primero (lost update).
 * Y después del UPDATE, nadie puede responder "¿por qué el saldo es este?".
 */
@Path("/api/naive/wallets")
public class NaiveWalletResource {

    public record OpenWallet(String owner, BigDecimal balance) {
    }

    public record Withdraw(BigDecimal amount) {
    }

    @Inject
    AgroalDataSource dataSource;

    @Inject
    LabSettings settings;

    @POST
    public Response open(OpenWallet request) {
        String id = "naive-" + UUID.randomUUID().toString().substring(0, 8);
        QuarkusTransaction.requiringNew().run(() -> execute("INSERT INTO naive_wallets VALUES (?, ?, ?)", id,
            request.owner(), request.balance()));
        return Response.status(201).entity(Map.of("id", id, "balance", request.balance())).build();
    }

    @POST
    @Path("/{id}/withdrawals")
    public Response withdraw(@PathParam("id") String id, Withdraw request) {
        return QuarkusTransaction.requiringNew().call(() -> {
            BigDecimal balance = balance(id);
            if (balance == null) {
                return Response.status(404).build();
            }
            if (balance.compareTo(request.amount()) < 0) {
                return Response.status(422).entity(Map.of("code", "INSUFFICIENT_FUNDS", "balance", balance)).build();
            }
            settings.think();
            BigDecimal newBalance = balance.subtract(request.amount());
            execute("UPDATE naive_wallets SET balance = ? WHERE id = ?", newBalance, id);
            return Response.ok(Map.of("id", id, "balance", newBalance)).build();
        });
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") String id) {
        BigDecimal balance = balance(id);
        return balance == null ? Response.status(404).build() : Response.ok(Map.of("id", id, "balance", balance)).build();
    }

    private BigDecimal balance(String id) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT balance FROM naive_wallets WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal(1) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private void execute(String sql, Object... params) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
