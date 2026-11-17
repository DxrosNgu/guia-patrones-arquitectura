package com.mercadoinventos.orders;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * El CONTRATO del legado para un pedido: {"id", "customer", "sku", "quantity", "total", "createdAt"}.
 * Lo usan el API público (el cliente no debe notar el cambio) y la sincronización con el legado.
 */
public record OrderJson(Long id, String customer, String sku, int quantity, BigDecimal total, String createdAt) {

    static OrderJson of(ShopOrder o) {
        return new OrderJson(o.id, o.customer, o.sku, o.quantity, o.total,
            o.createdAt.atOffset(ZoneOffset.UTC).toString());
    }

    ShopOrder toLegacyCopy() {
        ShopOrder order = new ShopOrder();
        order.id = id;
        order.customer = customer;
        order.sku = sku;
        order.quantity = quantity;
        order.total = total;
        order.createdAt = OffsetDateTime.parse(createdAt).toInstant();
        order.origin = ShopOrder.LEGACY;
        order.syncedToLegacy = true;   // vino del legado: no hay que mandárselo de vuelta
        return order;
    }
}
