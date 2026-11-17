package com.mercadoinventos.wallet.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mercadoinventos.wallet.domain.Wallet;
import com.mercadoinventos.wallet.domain.WalletEvent;
import com.mercadoinventos.wallet.domain.WalletException;
import com.mercadoinventos.wallet.es.ConcurrencyException;
import com.mercadoinventos.wallet.es.EventStore;
import com.mercadoinventos.wallet.es.SnapshotStore;
import com.mercadoinventos.wallet.es.StoredEvent;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/** Carga el monedero desde sus eventos, ejecuta comandos y guarda lo que decidieron. */
@ApplicationScoped
public class WalletService {

    private static final Logger LOG = Logger.getLogger(WalletService.class);

    /** Un monedero reconstruido, y cuánto trabajo costó: desde qué snapshot y cuántos eventos se releyeron. */
    public record Loaded(Wallet wallet, int snapshotVersion, int eventsReplayed) {
    }

    /** El resultado de un comando: el monedero después, los eventos que produjo y en qué intento se guardó. */
    public record Outcome(Wallet wallet, List<WalletEvent> events, int attempts) {
    }

    @Inject
    EventStore store;

    @Inject
    SnapshotStore snapshots;

    @Inject
    EventCodec codec;

    @Inject
    ObjectMapper mapper;

    @Inject
    LabSettings settings;

    @ConfigProperty(name = "wallet.snapshot-every")
    int snapshotEvery;

    /** El estado ACTUAL: el último snapshot (si hay) + los eventos posteriores. */
    public Loaded load(String walletId) {
        Wallet wallet = snapshots.find(walletId, Wallet.State.SCHEMA)
            .map(s -> Wallet.fromSnapshot(walletId, s.version(), mapper.convertValue(s.state(), Wallet.State.class)))
            .orElseGet(() -> Wallet.empty(walletId));
        int fromVersion = wallet.version();
        List<StoredEvent> events = store.readStream(walletId, fromVersion, null);
        events.forEach(e -> wallet.apply(codec.decode(e)));
        return new Loaded(wallet, fromVersion, events.size());
    }

    /** VIAJE EN EL TIEMPO: el estado en un instante pasado. Se rearma desde el primer evento, sin snapshot. */
    public Wallet at(String walletId, Instant instant) {
        List<WalletEvent> history = store.readStream(walletId, 0, instant).stream().map(codec::decode).toList();
        return Wallet.fromHistory(walletId, history);
    }

    /** La historia completa, ya en el formato actual de cada evento. */
    public List<StoredEvent> history(String walletId) {
        return store.readStream(walletId, 0, null);
    }

    public Outcome open(String owner) {
        String id = "wallet-" + UUID.randomUUID().toString().substring(0, 8);
        return QuarkusTransaction.requiringNew().call(() -> save(Wallet.empty(id), 0, w -> w.open(owner), "OpenWallet", 1));
    }

    /**
     * Ejecuta un comando con CONCURRENCIA OPTIMISTA: leer, decidir y guardar "si nadie escribió mientras tanto".
     * Si alguien escribió (ConcurrencyException), se vuelve a LEER y a DECIDIR con el estado nuevo: la segunda
     * decisión puede ser distinta (el saldo ya no alcanza).
     */
    public Outcome handle(String walletId, String command, Function<Wallet, List<WalletEvent>> decide) {
        for (int attempt = 1; ; attempt++) {
            final int current = attempt;
            try {
                return QuarkusTransaction.requiringNew().call(() -> {
                    Loaded loaded = load(walletId);
                    if (!loaded.wallet().exists()) {
                        throw new WalletNotFoundException(walletId);
                    }
                    return save(loaded.wallet(), loaded.snapshotVersion(), decide, command, current);
                });
            } catch (ConcurrencyException e) {
                if (attempt >= settings.maxAttempts()) {
                    LOG.warnf("%s. Sin más intentos (%d).", e.getMessage(), attempt);
                    throw e;
                }
                LOG.infof("%s. Reintento %d: releer y volver a decidir.", e.getMessage(), attempt + 1);
            }
        }
    }

    private Outcome save(Wallet wallet, int snapshotVersion, Function<Wallet, List<WalletEvent>> decide,
                         String command, int attempt) {
        int expectedVersion = wallet.version();
        List<WalletEvent> events = decide.apply(wallet);
        settings.think();
        ObjectNode metadata = mapper.createObjectNode().put("command", command).put("attempt", attempt);
        store.append(wallet.id(), expectedVersion, events.stream().map(e -> codec.encode(e, metadata)).toList());
        events.forEach(wallet::apply);
        if (wallet.version() - snapshotVersion >= snapshotEvery) {
            snapshots.save(wallet.id(), wallet.version(), Wallet.State.SCHEMA, mapper.valueToTree(wallet.state()));
            LOG.debugf("Snapshot de %s en la versión %d", wallet.id(), wallet.version());
        }
        return new Outcome(wallet, events, attempt);
    }

    public static class WalletNotFoundException extends WalletException {
        public WalletNotFoundException(String id) {
            super("No existe el monedero " + id);
        }
    }
}
