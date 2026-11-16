package com.mercadoinventos.orders;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Caos controlado: matar el proceso (como un kill -9) justo DESPUÉS del commit.
 * Un archivo marcador hace que pase una sola vez, aunque el contenedor se reinicie.
 */
@ApplicationScoped
public class Chaos {

    private static final Logger LOG = Logger.getLogger(Chaos.class);
    private static final Path MARKER = Path.of("/tmp/chaos-crashed");

    private final boolean crashAfterCommit;

    Chaos(@ConfigProperty(name = "chaos.crash-after-commit") boolean crashAfterCommit) {
        this.crashAfterCommit = crashAfterCommit;
    }

    public void afterCommit(String what) {
        if (!crashAfterCommit || Files.exists(MARKER)) {
            return;
        }
        try {
            Files.createFile(MARKER);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        LOG.errorf("💥 CAOS: el proceso muere justo después de confirmar %s en la base", what);
        Runtime.getRuntime().halt(1);   // sin shutdown ordenado: como un corte de luz
    }
}
