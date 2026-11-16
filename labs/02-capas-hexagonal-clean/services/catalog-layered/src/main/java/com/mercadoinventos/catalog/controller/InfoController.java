package com.mercadoinventos.catalog.controller;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.util.Map;

@Path("/api/info")
public class InfoController {

    @GET
    public Map<String, String> info() {
        // En capas, la persistencia no se puede cambiar por configuración: siempre es Postgres.
        return Map.of("architecture", "layered", "persistence", "postgres");
    }
}
