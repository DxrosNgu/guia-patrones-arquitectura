package com.mercadoinventos.catalog.domain;

/** Una regla de negocio no se cumple. El dominio no sabe nada de HTTP: el adaptador REST decide el código. */
public class BusinessRuleViolation extends RuntimeException {

    private final Rule rule;

    public BusinessRuleViolation(Rule rule, String message) {
        super(message);
        this.rule = rule;
    }

    public Rule rule() {
        return rule;
    }
}
