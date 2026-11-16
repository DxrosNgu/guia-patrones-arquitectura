package com.mercadoinventos.catalog.domain;

/** Reglas de negocio de "publicar un invento". El código viaja tal cual hasta la respuesta HTTP. */
public enum Rule {
    PRICE_MUST_BE_POSITIVE,
    INVENTOR_REQUIRED,
    NAME_REQUIRED,
    NAME_ALREADY_USED,
    TOO_MANY_DRAFTS,
    IMAGE_REQUIRED,
    ALREADY_PUBLISHED
}
