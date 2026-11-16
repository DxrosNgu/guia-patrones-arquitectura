package com.mercadoinventos.catalog.domain;

import java.util.List;

/**
 * Servicio de dominio: reglas que necesitan ver los OTROS inventos del inventor.
 * Recibe los datos como parámetro; no sabe de dónde salieron (Postgres, memoria...).
 */
public final class DraftPolicy {

    public static final int MAX_DRAFTS = 3;

    private DraftPolicy() {
    }

    public static void checkCanCreate(String name, List<Invention> inventorsInventions) {
        if (name != null && inventorsInventions.stream().anyMatch(i -> i.hasSameNameAs(name))) {
            throw new BusinessRuleViolation(Rule.NAME_ALREADY_USED, "Ya tienes un invento llamado '" + name.trim() + "'");
        }
        long drafts = inventorsInventions.stream().filter(Invention::isDraft).count();
        if (drafts >= MAX_DRAFTS) {
            throw new BusinessRuleViolation(Rule.TOO_MANY_DRAFTS,
                    "Máximo " + MAX_DRAFTS + " borradores por inventor: publica o borra alguno");
        }
    }
}
