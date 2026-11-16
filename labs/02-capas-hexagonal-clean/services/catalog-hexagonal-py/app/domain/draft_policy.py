"""Servicio de dominio: reglas que necesitan ver los OTROS inventos del inventor.

Recibe los datos como parámetro; no sabe de dónde salieron (Postgres, memoria...).
"""
from collections.abc import Iterable

from app.domain.invention import BusinessRuleViolation, Invention, Rule

MAX_DRAFTS = 3


def check_can_create(name: str, inventors_inventions: Iterable[Invention]) -> None:
    inventions = list(inventors_inventions)
    if any(i.has_same_name_as(name) for i in inventions):
        raise BusinessRuleViolation(Rule.NAME_ALREADY_USED, f"Ya tienes un invento llamado '{name.strip()}'")
    if sum(1 for i in inventions if i.is_draft) >= MAX_DRAFTS:
        raise BusinessRuleViolation(
            Rule.TOO_MANY_DRAFTS, f"Máximo {MAX_DRAFTS} borradores por inventor: publica o borra alguno")
