package com.mercadoinventos.catalog.application.port.out;

import com.mercadoinventos.catalog.domain.Invention;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de SALIDA: lo que la aplicación necesita del mundo exterior.
 * La define la aplicación (en sus términos) y la implementan los adaptadores
 * (Postgres, memoria). Así la dependencia apunta hacia adentro.
 */
public interface InventionRepository {

    void save(Invention invention);

    Optional<Invention> findById(UUID id);

    List<Invention> findByInventor(String inventorId);

    /** Nombre del adaptador, solo para mostrarlo en /api/info. */
    String name();
}
