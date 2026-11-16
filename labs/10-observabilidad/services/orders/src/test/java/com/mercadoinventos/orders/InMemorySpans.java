package com.mercadoinventos.orders;

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/** En los tests, Quarkus también exporta los spans aquí, además de por OTLP. */
@ApplicationScoped
public class InMemorySpans {

    @Produces
    @Singleton
    InMemorySpanExporter exporter() {
        return InMemorySpanExporter.create();
    }
}
