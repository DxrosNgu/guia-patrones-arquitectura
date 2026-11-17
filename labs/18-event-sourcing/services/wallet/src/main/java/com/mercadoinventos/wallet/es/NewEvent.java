package com.mercadoinventos.wallet.es;

import com.fasterxml.jackson.databind.node.ObjectNode;

/** Un evento listo para guardar. La versión dentro del stream la asigna el EventStore. */
public record NewEvent(String type, int schemaVersion, ObjectNode payload, ObjectNode metadata) {
}
