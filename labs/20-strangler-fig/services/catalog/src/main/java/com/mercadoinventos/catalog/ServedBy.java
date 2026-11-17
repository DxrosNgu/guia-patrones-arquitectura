package com.mercadoinventos.catalog;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

/** Cada respuesta dice quién la atendió: así se ve, desde afuera, cómo avanza la migración. */
@Provider
public class ServedBy implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        response.getHeaders().putSingle("X-Served-By", "catalog");
    }
}
