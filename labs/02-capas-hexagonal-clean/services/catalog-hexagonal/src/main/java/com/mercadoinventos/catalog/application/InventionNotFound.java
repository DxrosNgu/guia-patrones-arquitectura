package com.mercadoinventos.catalog.application;

import java.util.UUID;

public class InventionNotFound extends RuntimeException {

    public InventionNotFound(UUID id) {
        super("No existe el invento " + id);
    }
}
