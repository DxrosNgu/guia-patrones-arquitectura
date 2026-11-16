package com.mercadoinventos.shop.catalog.internal;

import com.mercadoinventos.shop.catalog.api.CatalogApi;
import com.mercadoinventos.shop.catalog.api.Product;
import io.quarkus.hibernate.orm.panache.Panache;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
class CatalogService implements CatalogApi {

    @Override
    public Optional<Product> findBySku(String sku) {
        return Optional.ofNullable(Panache.getEntityManager().find(ProductEntity.class, sku)).map(CatalogService::toApi);
    }

    @Override
    public List<Product> listAll() {
        return Panache.getEntityManager()
                .createQuery("from ProductEntity order by sku", ProductEntity.class)
                .getResultStream().map(CatalogService::toApi).toList();
    }

    private static Product toApi(ProductEntity e) {
        return new Product(e.sku, e.name, e.price);
    }
}
