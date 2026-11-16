package com.mercadoinventos.catalog;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuración tipada de la app (prefijo {@code app.} en application.properties).
 *
 * Quarkus la valida AL ARRANCAR: si falta {@code app.environment}, la app no
 * levanta y el error dice exactamente qué propiedad falta. Eso es "fail fast":
 * mejor no arrancar que arrancar con una configuración incompleta.
 */
@ConfigMapping(prefix = "app")
public interface AppConfig {

    /** Nombre del ambiente: local, dev, prod... Solo informativo, el código NO decide nada con él. */
    String environment();

    /** Feature flag: mostrar la sección de inventos destacados. */
    @WithDefault("false")
    boolean featuredEnabled();
}
