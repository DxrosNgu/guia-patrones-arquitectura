package com.mercadoinventos.catalog;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.Logger;

/** Deja en el log con qué configuración arrancó la instancia. */
class StartupLog {

    private static final Logger LOG = Logger.getLogger(StartupLog.class);

    void onStart(@Observes StartupEvent event, AppConfig config) {
        LOG.infof("Catálogo iniciado en el ambiente '%s' (destacados: %s)",
            config.environment(), config.featuredEnabled() ? "activados" : "desactivados");
    }
}
