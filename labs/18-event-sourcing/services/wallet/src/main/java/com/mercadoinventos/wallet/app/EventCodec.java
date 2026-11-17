package com.mercadoinventos.wallet.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mercadoinventos.wallet.domain.WalletEvent;
import com.mercadoinventos.wallet.es.NewEvent;
import com.mercadoinventos.wallet.es.StoredEvent;
import com.mercadoinventos.wallet.es.Upcaster;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;

/** Traduce entre los eventos del dominio (records de Java) y las filas del event store (JSON). */
@ApplicationScoped
public class EventCodec {

    private static final Map<String, Class<? extends WalletEvent>> TYPES = Map.of(
        "WalletOpened", WalletEvent.WalletOpened.class,
        "FundsDeposited", WalletEvent.FundsDeposited.class,
        "FundsWithdrawn", WalletEvent.FundsWithdrawn.class,
        "WithdrawalRejected", WalletEvent.WithdrawalRejected.class);

    @Inject
    ObjectMapper mapper;

    public NewEvent encode(WalletEvent event, ObjectNode metadata) {
        String type = event.getClass().getSimpleName();
        return new NewEvent(type, Upcaster.CURRENT.get(type), mapper.valueToTree(event), metadata);
    }

    /** Lee un evento guardado: primero lo lleva al formato actual, después lo convierte en un record. */
    public WalletEvent decode(StoredEvent stored) {
        StoredEvent current = Upcaster.upcast(stored);
        try {
            return mapper.treeToValue(current.payload(), TYPES.get(current.type()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Evento ilegible: " + stored.streamId() + " v" + stored.version(), e);
        }
    }
}
