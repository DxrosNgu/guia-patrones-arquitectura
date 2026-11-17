package com.mercadoinventos.wallet.es;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;

/** Una fila del event store, tal como está guardada (el payload puede estar en un formato viejo). */
public record StoredEvent(long position, String streamId, int version, String type, int schemaVersion,
                          ObjectNode payload, ObjectNode metadata, Instant occurredAt) {

    public StoredEvent withPayload(int newSchemaVersion, ObjectNode newPayload) {
        return new StoredEvent(position, streamId, version, type, newSchemaVersion, newPayload, metadata, occurredAt);
    }
}
