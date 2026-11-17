package com.mercadoinventos.wallet.api;

import com.mercadoinventos.wallet.app.LabSettings;
import com.mercadoinventos.wallet.projection.Projector;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import java.util.Map;

/** Perillas del lab. En un servicio real no existirían. */
@Path("/admin")
public class AdminResource {

    public record ThinkTime(int ms) {
    }

    public record Retries(int maxAttempts) {
    }

    @Inject
    LabSettings settings;

    @Inject
    Projector projector;

    @PUT
    @Path("/think-time")
    public Map<String, Object> thinkTime(ThinkTime request) {
        settings.setThinkTimeMs(request.ms());
        return Map.of("thinkTimeMs", settings.thinkTimeMs());
    }

    @PUT
    @Path("/retries")
    public Map<String, Object> retries(Retries request) {
        settings.setMaxAttempts(request.maxAttempts());
        return Map.of("maxAttempts", settings.maxAttempts());
    }

    @GET
    @Path("/projections")
    public Map<String, Object> projections() {
        long[] status = projector.status();
        return Map.of("checkpoint", status[0], "pending", status[1]);
    }

    @POST
    @Path("/projections/rebuild")
    public Response rebuild() {
        projector.rebuild();
        return Response.accepted(Map.of("message", "Vistas borradas; el proyector las rearma desde el primer evento")).build();
    }
}
