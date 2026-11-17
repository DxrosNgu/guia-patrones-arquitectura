package com.mercadoinventos.wallet.api;

import com.mercadoinventos.wallet.projection.ViewQueries;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.util.List;
import java.util.Map;

/** Todos los saldos, desde la PROYECCIÓN balance_view: una consulta, sin releer eventos. */
@Path("/api/balances")
public class BalancesResource {

    @Inject
    ViewQueries views;

    @GET
    public List<Map<String, Object>> balances() {
        return views.balances();
    }
}
