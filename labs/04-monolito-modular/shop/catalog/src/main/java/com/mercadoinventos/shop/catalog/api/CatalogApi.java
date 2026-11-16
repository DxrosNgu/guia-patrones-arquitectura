package com.mercadoinventos.shop.catalog.api;

import java.util.List;
import java.util.Optional;

/** API PÚBLICA del módulo Catálogo. Es lo único que los demás módulos pueden usar. */
public interface CatalogApi {

    Optional<Product> findBySku(String sku);

    List<Product> listAll();
}
