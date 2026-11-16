package com.mercadoinventos.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mercadoinventos.catalog.adapter.out.memory.InMemoryInventionRepository;
import com.mercadoinventos.catalog.application.port.in.CreateDraftCommand;
import com.mercadoinventos.catalog.domain.Invention;
import com.mercadoinventos.catalog.domain.InventionStatus;
import com.mercadoinventos.catalog.domain.Rule;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Los casos de uso se prueban con el adaptador en memoria: sin Quarkus y sin Postgres.
 * Es posible porque la aplicación depende de un PUERTO (InventionRepository), no de JPA.
 */
class CatalogServiceTest {

    private final CatalogService catalog = new CatalogService(new InMemoryInventionRepository());

    @Test
    void createsADraftAndPublishesItAfterAddingAnImage() {
        Invention draft = catalog.createDraft(new CreateDraftCommand("ana", "Paraguas solar", new BigDecimal("49.90"), null));

        catalog.changeImage(draft.id(), "https://img.example/paraguas.png");
        Invention published = catalog.publish(draft.id());

        assertThat(published.status()).isEqualTo(InventionStatus.PUBLISHED);
        assertThat(catalog.listByInventor("ana")).extracting(Invention::name).containsExactly("Paraguas solar");
    }

    @Test
    void appliesTheDraftPolicyWithTheInventorsInventions() {
        catalog.createDraft(new CreateDraftCommand("ana", "A", BigDecimal.ONE, null));
        catalog.createDraft(new CreateDraftCommand("ana", "B", BigDecimal.ONE, null));
        catalog.createDraft(new CreateDraftCommand("ana", "C", BigDecimal.ONE, null));

        assertThatThrownBy(() -> catalog.createDraft(new CreateDraftCommand("ana", "D", BigDecimal.ONE, null)))
                .extracting("rule").isEqualTo(Rule.TOO_MANY_DRAFTS);
        // Otro inventor no se ve afectado.
        assertThat(catalog.createDraft(new CreateDraftCommand("beto", "A", BigDecimal.ONE, null)).status())
                .isEqualTo(InventionStatus.DRAFT);
    }

    @Test
    void unknownInventionIsNotFound() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> catalog.publish(unknown)).isInstanceOf(InventionNotFound.class);
    }
}
