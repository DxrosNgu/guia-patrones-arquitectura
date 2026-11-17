package com.mercadoinventos.wallet.api;

import com.mercadoinventos.wallet.app.EventCodec;
import com.mercadoinventos.wallet.app.WalletService;
import com.mercadoinventos.wallet.app.WalletService.Outcome;
import com.mercadoinventos.wallet.domain.Wallet;
import com.mercadoinventos.wallet.domain.WalletEvent;
import com.mercadoinventos.wallet.domain.WalletEvent.WithdrawalRejected;
import com.mercadoinventos.wallet.es.StoredEvent;
import com.mercadoinventos.wallet.es.Upcaster;
import com.mercadoinventos.wallet.projection.ViewQueries;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/api/wallets")
public class WalletResource {

    public record OpenWallet(String owner) {
    }

    public record Deposit(BigDecimal amount, String channel, String description) {
    }

    public record Withdraw(BigDecimal amount, String description) {
    }

    @Inject
    WalletService wallets;

    @Inject
    EventCodec codec;

    @Inject
    ViewQueries views;

    @ConfigProperty(name = "wallet.zone")
    ZoneId zone;

    @POST
    public Response open(OpenWallet request) {
        Outcome outcome = wallets.open(request == null ? null : request.owner());
        return Response.status(201).entity(view(outcome.wallet())).build();
    }

    @POST
    @Path("/{id}/deposits")
    public Response deposit(@PathParam("id") String id, Deposit request) {
        Outcome outcome = wallets.handle(id, "Deposit", w -> w.deposit(request.amount(), request.channel(), request.description()));
        return Response.ok(result(outcome)).build();
    }

    @POST
    @Path("/{id}/withdrawals")
    public Response withdraw(@PathParam("id") String id, Withdraw request) {
        Outcome outcome = wallets.handle(id, "Withdraw", w -> w.withdraw(request.amount(), request.description()));
        boolean rejected = outcome.events().getFirst() instanceof WithdrawalRejected;
        // 422: el retiro NO se hizo, pero el rechazo quedó registrado como un evento más de la historia.
        return Response.status(rejected ? 422 : 200).entity(result(outcome)).build();
    }

    /** El saldo se calcula AHORA, desde los eventos. Con ?at=2026-11-01, el saldo de ese momento. */
    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") String id, @QueryParam("at") String at) {
        if (at != null) {
            Instant instant = parseInstant(at);
            Wallet wallet = wallets.at(id, instant);
            if (!wallet.exists()) {
                return Response.status(404).entity(Map.of("message", "El monedero no existía en " + at)).build();
            }
            Map<String, Object> body = view(wallet);
            body.put("at", instant.toString());
            return Response.ok(body).build();
        }
        WalletService.Loaded loaded = wallets.load(id);
        if (!loaded.wallet().exists()) {
            return Response.status(404).entity(Map.of("message", "No existe el monedero " + id)).build();
        }
        return Response.ok(view(loaded.wallet()))
            .header("X-Snapshot-Version", loaded.snapshotVersion())
            .header("X-Events-Replayed", loaded.eventsReplayed())
            .build();
    }

    /** La historia: cada evento como está guardado, ya llevado al formato actual, y el saldo después de cada uno. */
    @GET
    @Path("/{id}/events")
    public List<Map<String, Object>> events(@PathParam("id") String id) {
        List<Map<String, Object>> result = new ArrayList<>();
        Wallet replay = Wallet.empty(id);
        for (StoredEvent stored : wallets.history(id)) {
            WalletEvent event = codec.decode(stored);
            replay.apply(event);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("version", stored.version());
            row.put("type", stored.type());
            row.put("occurredAt", stored.occurredAt().toString());
            row.put("storedSchemaVersion", stored.schemaVersion());
            row.put("schemaVersion", Upcaster.CURRENT.get(stored.type()));
            row.put("data", event);
            row.put("metadata", stored.metadata());
            row.put("balanceAfter", replay.balance());
            result.add(row);
        }
        return result;
    }

    /** Movimientos por mes: sale de una PROYECCIÓN (tabla), no de releer eventos. Puede ir un poco atrasada. */
    @GET
    @Path("/{id}/monthly")
    public List<Map<String, Object>> monthly(@PathParam("id") String id) {
        return views.monthly(id);
    }

    private Instant parseInstant(String at) {
        try {
            return at.length() == 10 ? LocalDate.parse(at).atStartOfDay(zone).toInstant() : OffsetDateTime.parse(at).toInstant();
        } catch (DateTimeParseException e) {
            throw new BadRequestException("Fecha inválida: usa 2026-11-01 o 2026-11-01T00:00:00-05:00");
        }
    }

    private static Map<String, Object> view(Wallet wallet) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", wallet.id());
        body.put("owner", wallet.owner());
        body.put("balance", wallet.balance());
        body.put("version", wallet.version());
        return body;
    }

    private static Map<String, Object> result(Outcome outcome) {
        Map<String, Object> body = view(outcome.wallet());
        body.put("events", outcome.events().stream().map(e -> e.getClass().getSimpleName()).toList());
        body.put("attempts", outcome.attempts());
        return body;
    }
}
