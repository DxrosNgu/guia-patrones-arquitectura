package com.mercadoinventos.catalog.application.port.in;

import java.math.BigDecimal;

/** Los datos de entrada del caso de uso. No es el DTO de REST: un CLI o un consumidor Kafka también lo usan. */
public record CreateDraftCommand(String inventorId, String name, BigDecimal price, String imageUrl) {
}
