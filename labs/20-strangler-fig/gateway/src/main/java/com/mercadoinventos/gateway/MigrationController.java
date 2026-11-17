package com.mercadoinventos.gateway;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Los mandos de la migración. ⚠️ Sin autenticación: en prod esto va protegido, o el estado sale
 * de la configuración versionada (Git) y no de una API.
 */
@RestController
@RequestMapping("/admin/migration")
public class MigrationController {

    public record Change(MigrationState.Target catalog, Integer ordersToNewPercent, Boolean legacyEnabled) {
    }

    private final MigrationState migration;

    MigrationController(MigrationState migration) {
        this.migration = migration;
    }

    @GetMapping
    public Map<String, Object> current() {
        return migration.view();
    }

    @PutMapping
    public Map<String, Object> change(@RequestBody Change change) {
        migration.update(change.catalog(), change.ordersToNewPercent(), change.legacyEnabled());
        return migration.view();
    }

    @PutMapping("/stage/{stage}")
    public Map<String, Object> stage(@PathVariable int stage) {
        migration.applyStage(stage);
        return migration.view();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> invalid(IllegalArgumentException e) {
        return Map.of("message", e.getMessage());
    }
}
