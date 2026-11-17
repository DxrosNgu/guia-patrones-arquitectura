package com.mercadoinventos.catalog;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.util.List;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/** El catálogo del legado, para la copia inicial. Después de la migración, este cliente se borra. */
@RegisterRestClient(configKey = "legacy")
@Path("/api/catalog/products")
public interface LegacyCatalog {

    @GET
    List<ProductJson> all();
}
