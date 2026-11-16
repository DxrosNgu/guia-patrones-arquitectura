package com.mercadoinventos.catalog.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class DraftPolicyTest {

    private static Invention draft(String name) {
        return Invention.draft("ana", name, BigDecimal.TEN, null);
    }

    private static Invention published(String name) {
        Invention invention = Invention.draft("ana", name, BigDecimal.TEN, "https://img.example/x.png");
        invention.publish();
        return invention;
    }

    @Test
    void nameMustBeUniquePerInventorIgnoringCaseAndSpaces() {
        List<Invention> existing = List.of(published("Paraguas solar"));

        assertThatThrownBy(() -> DraftPolicy.checkCanCreate("  paraguas SOLAR ", existing))
                .extracting("rule").isEqualTo(Rule.NAME_ALREADY_USED);
    }

    @Test
    void atMostThreeDraftsPerInventor() {
        List<Invention> existing = List.of(draft("A"), draft("B"), draft("C"));

        assertThatThrownBy(() -> DraftPolicy.checkCanCreate("D", existing))
                .extracting("rule").isEqualTo(Rule.TOO_MANY_DRAFTS);
    }

    @Test
    void publishedInventionsDoNotCountAsDrafts() {
        List<Invention> existing = List.of(draft("A"), draft("B"), published("C"), published("D"));

        assertThatCode(() -> DraftPolicy.checkCanCreate("E", existing)).doesNotThrowAnyException();
    }
}
