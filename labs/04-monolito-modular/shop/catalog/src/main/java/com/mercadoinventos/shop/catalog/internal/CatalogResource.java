package com.mercadoinventos.shop.catalog.internal;

import com.mercadoinventos.shop.catalog.api.CatalogApi;
import com.mercadoinventos.shop.catalog.api.Product;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.util.List;

@Path("/api/catalog/products")
public class CatalogResource {

    @Inject
    CatalogApi catalog;

    @GET
    public List<Product> list() {
        return catalog.listAll();
    }
}
