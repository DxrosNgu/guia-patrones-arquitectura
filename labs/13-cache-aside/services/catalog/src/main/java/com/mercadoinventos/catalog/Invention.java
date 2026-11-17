package com.mercadoinventos.catalog;

import java.math.BigDecimal;

public record Invention(String sku, String name, BigDecimal price) {
}
