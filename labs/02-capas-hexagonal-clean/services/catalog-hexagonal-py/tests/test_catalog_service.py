"""Los casos de uso se prueban con el adaptador en memoria: sin FastAPI y sin Postgres."""
from decimal import Decimal
from uuid import uuid4

import pytest

from app.adapters.memory_repository import InMemoryInventionRepository
from app.application.catalog_service import CatalogService, CreateDraft, InventionNotFound
from app.domain.invention import BusinessRuleViolation, InventionStatus, Rule


@pytest.fixture
def catalog():
    return CatalogService(InMemoryInventionRepository())


def test_create_and_publish(catalog):
    draft = catalog.create_draft(CreateDraft("ana", "Paraguas solar", Decimal("49.90")))

    catalog.change_image(draft.id, "https://img.example/p.png")
    published = catalog.publish(draft.id)

    assert published.status is InventionStatus.PUBLISHED
    assert [i.name for i in catalog.list_by_inventor("ana")] == ["Paraguas solar"]


def test_draft_policy_uses_the_inventors_inventions(catalog):
    for name in "ABC":
        catalog.create_draft(CreateDraft("ana", name, Decimal("1")))

    with pytest.raises(BusinessRuleViolation) as error:
        catalog.create_draft(CreateDraft("ana", "D", Decimal("1")))
    assert error.value.rule is Rule.TOO_MANY_DRAFTS
    assert catalog.create_draft(CreateDraft("beto", "A", Decimal("1"))).status is InventionStatus.DRAFT


def test_unknown_invention(catalog):
    with pytest.raises(InventionNotFound):
        catalog.publish(uuid4())
