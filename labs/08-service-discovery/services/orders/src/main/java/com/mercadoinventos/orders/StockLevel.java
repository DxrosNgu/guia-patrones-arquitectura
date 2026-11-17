package com.mercadoinventos.orders;

/** Lo que responde inventario, incluido QUÉ instancia respondió. */
public record StockLevel(String sku, int available, String instance) {
}
