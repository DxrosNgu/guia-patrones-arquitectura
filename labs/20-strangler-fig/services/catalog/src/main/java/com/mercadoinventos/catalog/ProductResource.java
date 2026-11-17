package com.mercadoinventos.catalog;

import io.quarkus.panache.common.Sort;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

@Path("/")
public class ProductResource {

    @Inject
    CatalogImport catalogImport;

    @GET
    @Path("/api/catalog/products")
    public List<ProductJson> all() {
        return Product.<Product>listAll(Sort.by("sku")).stream().map(ProductJson::of).toList();
    }

    @GET
    @Path("/api/catalog/products/{sku}")
    public Response one(@PathParam("sku") String sku) {
        Product product = Product.findById(sku);
        return product == null
            ? Response.status(404).entity(Map.of("message", "No existe " + sku)).build()
            : Response.ok(ProductJson.of(product)).build();
    }

    @POST
    @Path("/admin/import")
    public Map<String, Object> importFromLegacy() {
        return Map.of("imported", catalogImport.importIfEmpty());
    }
}
