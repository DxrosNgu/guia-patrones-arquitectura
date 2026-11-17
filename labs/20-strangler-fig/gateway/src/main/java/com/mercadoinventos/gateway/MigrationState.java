package com.mercadoinventos.gateway;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

/**
 * El ESTADO DE LA MIGRACIÓN: a dónde va cada ruta. Vive en memoria y se cambia en caliente
 * (PUT /admin/migration), sin reiniciar el gateway ni los servicios.
 */
@Component
public class MigrationState {

    public enum Target { LEGACY, NEW }

    private volatile Target catalog = Target.LEGACY;
    private volatile int ordersToNewPercent = 0;
    private volatile boolean legacyEnabled = true;
    private volatile int stage = 1;

    /** Las 5 etapas del lab, como atajos. */
    public synchronized void applyStage(int newStage) {
        switch (newStage) {
            case 1 -> set(Target.LEGACY, 0, true);     // proxy transparente
            case 2 -> set(Target.NEW, 0, true);        // /api/catalog al servicio nuevo
            case 3 -> set(Target.NEW, 10, true);       // canary: 10 % de /api/orders al nuevo
            case 4 -> set(Target.NEW, 100, true);      // todo /api/orders al nuevo; el legado solo recibe la sincronización
            case 5 -> set(Target.NEW, 100, false);     // el legado ya no recibe tráfico: se puede apagar
            default -> throw new IllegalArgumentException("Las etapas van de 1 a 5");
        }
        stage = newStage;
    }

    public synchronized void update(Target newCatalog, Integer newPercent, Boolean newLegacyEnabled) {
        set(newCatalog != null ? newCatalog : catalog,
            newPercent != null ? newPercent : ordersToNewPercent,
            newLegacyEnabled != null ? newLegacyEnabled : legacyEnabled);
        stage = 0;   // configuración a mano: ya no es una etapa "de libro"
    }

    private void set(Target newCatalog, int newPercent, boolean newLegacyEnabled) {
        if (newPercent < 0 || newPercent > 100) {
            throw new IllegalArgumentException("ordersToNewPercent va de 0 a 100");
        }
        if (!newLegacyEnabled && (newCatalog == Target.LEGACY || newPercent < 100)) {
            throw new IllegalArgumentException("No se puede apagar el legado mientras todavía recibe tráfico");
        }
        catalog = newCatalog;
        ordersToNewPercent = newPercent;
        legacyEnabled = newLegacyEnabled;
    }

    boolean catalogOnNew() {
        return catalog == Target.NEW;
    }

    /** El canary: cada petición se sortea. Con 10, una de cada diez (en promedio) va al servicio nuevo. */
    boolean orderToNew() {
        int percent = ordersToNewPercent;
        return percent >= 100 || (percent > 0 && ThreadLocalRandom.current().nextInt(100) < percent);
    }

    boolean legacyEnabled() {
        return legacyEnabled;
    }

    public Map<String, Object> view() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("stage", stage == 0 ? "manual" : stage);
        view.put("catalog", catalog);
        view.put("ordersToNewPercent", ordersToNewPercent);
        view.put("legacyEnabled", legacyEnabled);
        return view;
    }
}
