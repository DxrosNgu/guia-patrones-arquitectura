package com.mercadoinventos.shop.orders.internal;

import com.mercadoinventos.shop.catalog.internal.ProductEntity;
import io.quarkus.hibernate.orm.panache.Panache;
import java.math.BigDecimal;

/**
 * ❌ El "atajo" de siempre: Pedidos lee directo la entidad interna del Catálogo
 * porque "es más rápido que pasar por CatalogApi". Compila sin problemas.
 * ArchUnit hace fallar el build.
 */
class AtajoAlCatalogo {

    BigDecimal price(String sku) {
        return Panache.getEntityManager().find(ProductEntity.class, sku).price;
    }
}
