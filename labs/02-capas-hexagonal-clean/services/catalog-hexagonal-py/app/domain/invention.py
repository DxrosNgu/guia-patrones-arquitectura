"""El invento del dominio: Python puro, sin FastAPI, Pydantic ni SQLAlchemy."""
from __future__ import annotations

from dataclasses import dataclass, field
from datetime import UTC, datetime
from decimal import ROUND_HALF_UP, Decimal
from enum import StrEnum
from uuid import UUID, uuid4


class Rule(StrEnum):
    """Reglas de negocio de "publicar un invento". El código viaja tal cual hasta la respuesta HTTP."""

    PRICE_MUST_BE_POSITIVE = "PRICE_MUST_BE_POSITIVE"
    INVENTOR_REQUIRED = "INVENTOR_REQUIRED"
    NAME_REQUIRED = "NAME_REQUIRED"
    NAME_ALREADY_USED = "NAME_ALREADY_USED"
    TOO_MANY_DRAFTS = "TOO_MANY_DRAFTS"
    IMAGE_REQUIRED = "IMAGE_REQUIRED"
    ALREADY_PUBLISHED = "ALREADY_PUBLISHED"


class BusinessRuleViolation(Exception):
    """Una regla no se cumple. El dominio no sabe de HTTP: el adaptador REST elige el código."""

    def __init__(self, rule: Rule, message: str):
        super().__init__(message)
        self.rule = rule
        self.message = message


class InventionStatus(StrEnum):
    DRAFT = "DRAFT"
    PUBLISHED = "PUBLISHED"


@dataclass(frozen=True)
class Price:
    """Value object: un precio inválido no puede existir."""

    amount: Decimal

    def __post_init__(self):
        if self.amount is None or Decimal(self.amount) <= 0:
            raise BusinessRuleViolation(Rule.PRICE_MUST_BE_POSITIVE, "El precio debe ser mayor que 0")
        object.__setattr__(self, "amount", Decimal(self.amount).quantize(Decimal("0.01"), ROUND_HALF_UP))


def _blank_to_none(value: str | None) -> str | None:
    return value.strip() if value and value.strip() else None


@dataclass
class Invention:
    inventor_id: str
    name: str
    price: Price
    image_url: str | None = None
    status: InventionStatus = InventionStatus.DRAFT
    id: UUID = field(default_factory=uuid4)
    created_at: datetime = field(default_factory=lambda: datetime.now(UTC))

    @classmethod
    def draft(cls, inventor_id: str, name: str, price: Decimal | None, image_url: str | None = None) -> Invention:
        if not inventor_id or not inventor_id.strip():
            raise BusinessRuleViolation(Rule.INVENTOR_REQUIRED, "Falta el inventor")
        if not name or not name.strip():
            raise BusinessRuleViolation(Rule.NAME_REQUIRED, "El nombre es obligatorio")
        return cls(inventor_id=inventor_id, name=name.strip(), price=Price(price), image_url=_blank_to_none(image_url))

    def change_image(self, image_url: str | None) -> None:
        self.image_url = _blank_to_none(image_url)

    def publish(self) -> None:
        if self.status is InventionStatus.PUBLISHED:
            raise BusinessRuleViolation(Rule.ALREADY_PUBLISHED, "El invento ya está publicado")
        if self.image_url is None:
            raise BusinessRuleViolation(Rule.IMAGE_REQUIRED, "No se puede publicar un invento sin imagen")
        self.status = InventionStatus.PUBLISHED

    @property
    def is_draft(self) -> bool:
        return self.status is InventionStatus.DRAFT

    def has_same_name_as(self, other: str) -> bool:
        return self.name.strip().casefold() == other.strip().casefold()
