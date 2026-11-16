package com.mercadoinventos.catalog;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Muestra con qué configuración está corriendo ESTA instancia.
 * Es la herramienta del lab para comparar ambientes: misma imagen, distinto resultado.
 */
@Path("/api/info")
public class InfoResource {

    public record Info(String service, String environment, boolean featuredEnabled,
                       String logLevel, boolean logJson, boolean swaggerEnabled) {
    }

    private final AppConfig config;
    private final String logLevel;
    private final boolean logJson;
    private final boolean swaggerEnabled;

    InfoResource(AppConfig config,
                 @ConfigProperty(name = "quarkus.log.category.\"com.mercadoinventos\".level") String logLevel,
                 @ConfigProperty(name = "quarkus.log.console.json.enabled") boolean logJson,
                 @ConfigProperty(name = "quarkus.swagger-ui.enabled") boolean swaggerEnabled) {
        this.config = config;
        this.logLevel = logLevel;
        this.logJson = logJson;
        this.swaggerEnabled = swaggerEnabled;
    }

    @GET
    public Info info() {
        return new Info("catalog", config.environment(), config.featuredEnabled(),
            logLevel, logJson, swaggerEnabled);
    }
}
