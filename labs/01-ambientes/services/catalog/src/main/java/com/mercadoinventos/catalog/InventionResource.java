package com.mercadoinventos.catalog;

import io.quarkus.panache.common.Sort;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.jboss.logging.Logger;

import java.util.List;

@Path("/api/inventions")
public class InventionResource {

    private static final Logger LOG = Logger.getLogger(InventionResource.class);

    @GET
    public List<Invention> list() {
        List<Invention> inventions = Invention.listAll(Sort.by("sku"));
        // Solo aparece con LOG_LEVEL=DEBUG (dev). En local y prod (INFO) no se ve.
        LOG.debugf("Listando %d inventos", inventions.size());
        return inventions;
    }
}
