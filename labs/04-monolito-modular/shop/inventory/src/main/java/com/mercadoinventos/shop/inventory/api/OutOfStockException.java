package com.mercadoinventos.shop.inventory.api;

/** Parte de la API pública: quien llama a reserve() tiene que poder capturarla. */
public class OutOfStockException extends RuntimeException {

    private final String sku;

    public OutOfStockException(String sku) {
        super("No hay stock suficiente de " + sku);
        this.sku = sku;
    }

    public String sku() {
        return sku;
    }
}
