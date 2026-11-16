package com.mercadoinventos.shop.orders.api;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Evento PÚBLICO del módulo Pedidos. Otros módulos (Envíos) lo escuchan sin que Pedidos
 * sepa que existen: la dependencia va de Envíos hacia Pedidos, nunca al revés.
 */
public record OrderPlaced(UUID orderId, String customerId, BigDecimal total, Map<String, Integer> quantitiesBySku) {
}
