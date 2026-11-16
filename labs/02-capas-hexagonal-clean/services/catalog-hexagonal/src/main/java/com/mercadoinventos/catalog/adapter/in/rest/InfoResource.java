package com.mercadoinventos.catalog.adapter.in.rest;

import com.mercadoinventos.catalog.application.port.out.InventionRepository;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.util.Map;

/** Qué arquitectura es este servicio y qué adaptador de persistencia está usando. */
@Path("/api/info")
public class InfoResource {

    @Inject
    InventionRepository repository;

    @GET
    public Map<String, String> info() {
        return Map.of("architecture", "hexagonal", "persistence", repository.name());
    }
}
