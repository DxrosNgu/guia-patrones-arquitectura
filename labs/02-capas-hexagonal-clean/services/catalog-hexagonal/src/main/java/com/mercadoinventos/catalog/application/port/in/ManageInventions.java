package com.mercadoinventos.catalog.application.port.in;

import com.mercadoinventos.catalog.domain.Invention;
import java.util.List;
import java.util.UUID;

/**
 * Puerto de ENTRADA: lo que la aplicación ofrece. Los adaptadores de entrada
 * (REST hoy; mañana un CLI o un consumidor de mensajes) dependen de esta interfaz.
 */
public interface ManageInventions {

    Invention createDraft(CreateDraftCommand command);

    Invention changeImage(UUID id, String imageUrl);

    Invention publish(UUID id);

    Invention get(UUID id);

    List<Invention> listByInventor(String inventorId);
}
