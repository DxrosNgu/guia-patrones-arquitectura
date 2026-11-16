package com.mercadoinventos.orders;

import io.opentelemetry.api.trace.Span;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

/**
 * Devuelve el traceId en cada respuesta (header X-Trace-Id).
 *
 * Es el "Correlation ID": cuando un usuario reporta un error, con este valor
 * encuentras en Grafana la traza completa y todos los logs de esa petición,
 * en todos los servicios.
 */
@Provider
public class TraceIdHeader implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        var context = Span.current().getSpanContext();
        if (context.isValid()) {
            response.getHeaders().putSingle("X-Trace-Id", context.getTraceId());
        }
    }
}
