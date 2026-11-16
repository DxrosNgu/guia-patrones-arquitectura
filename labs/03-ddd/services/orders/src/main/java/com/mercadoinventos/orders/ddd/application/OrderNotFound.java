package com.mercadoinventos.orders.ddd.application;

import java.util.UUID;

public class OrderNotFound extends RuntimeException {

    public OrderNotFound(UUID id) {
        super("No existe el pedido " + id);
    }
}
