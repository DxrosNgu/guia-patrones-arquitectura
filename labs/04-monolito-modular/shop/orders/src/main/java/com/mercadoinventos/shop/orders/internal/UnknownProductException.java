package com.mercadoinventos.shop.orders.internal;

public class UnknownProductException extends RuntimeException {

    public UnknownProductException(String sku) {
        super("No existe el producto " + sku);
    }
}
