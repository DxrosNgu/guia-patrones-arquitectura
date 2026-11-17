package com.mercadoinventos.orders;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import java.util.Map;

@Path("/admin/sync")
public class AdminResource {

    public record Toggle(boolean enabled) {
    }

    @Inject
    LegacySync sync;

    @GET
    public Map<String, Object> status() {
        return sync.status();
    }

    @PUT
    public Map<String, Object> toggle(Toggle request) {
        sync.setEnabled(request.enabled());
        return sync.status();
    }
}
