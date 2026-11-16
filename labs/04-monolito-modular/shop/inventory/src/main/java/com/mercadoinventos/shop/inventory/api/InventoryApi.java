package com.mercadoinventos.shop.inventory.api;

import java.util.Map;
import java.util.UUID;

/** API PÚBLICA del módulo Inventario. */
public interface InventoryApi {

    /**
     * Reserva todas las unidades o ninguna. Se une a la transacción de quien llama:
     * si después algo falla (por ejemplo el pago), la reserva se deshace sola.
     */
    void reserve(UUID orderId, Map<String, Integer> quantitiesBySku);
}
