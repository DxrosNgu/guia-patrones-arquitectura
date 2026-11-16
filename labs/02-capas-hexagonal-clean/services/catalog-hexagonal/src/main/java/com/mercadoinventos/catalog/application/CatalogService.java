package com.mercadoinventos.catalog.application;

import com.mercadoinventos.catalog.application.port.in.CreateDraftCommand;
import com.mercadoinventos.catalog.application.port.in.ManageInventions;
import com.mercadoinventos.catalog.application.port.out.InventionRepository;
import com.mercadoinventos.catalog.domain.DraftPolicy;
import com.mercadoinventos.catalog.domain.Invention;
import java.util.List;
import java.util.UUID;

/**
 * Los casos de uso: orquestan el dominio y los puertos. Sin anotaciones de Quarkus:
 * el bean se crea en {@code config.ApplicationConfig}. Las transacciones son un
 * detalle de infraestructura y las pone el adaptador de entrada.
 */
public class CatalogService implements ManageInventions {

    private final InventionRepository inventions;

    public CatalogService(InventionRepository inventions) {
        this.inventions = inventions;
    }

    @Override
    public Invention createDraft(CreateDraftCommand command) {
        // 1) Reglas del propio invento (precio, nombre...).
        Invention invention = Invention.draft(command.inventorId(), command.name(), command.price(), command.imageUrl());
        // 2) Reglas que miran los otros inventos del inventor.
        DraftPolicy.checkCanCreate(invention.name(), inventions.findByInventor(invention.inventorId()));
        inventions.save(invention);
        return invention;
    }

    @Override
    public Invention changeImage(UUID id, String imageUrl) {
        Invention invention = get(id);
        invention.changeImage(imageUrl);
        inventions.save(invention);
        return invention;
    }

    @Override
    public Invention publish(UUID id) {
        Invention invention = get(id);
        invention.publish();
        inventions.save(invention);
        return invention;
    }

    @Override
    public Invention get(UUID id) {
        return inventions.findById(id).orElseThrow(() -> new InventionNotFound(id));
    }

    @Override
    public List<Invention> listByInventor(String inventorId) {
        return inventions.findByInventor(inventorId);
    }
}
