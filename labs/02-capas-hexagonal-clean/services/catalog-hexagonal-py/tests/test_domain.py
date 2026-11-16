"""Tests del dominio: Python puro. Sin FastAPI, sin base de datos: corren en milisegundos."""
from decimal import Decimal

import pytest

from app.domain.draft_policy import check_can_create
from app.domain.invention import BusinessRuleViolation, Invention, InventionStatus, Price, Rule


def test_a_new_invention_is_a_draft():
    invention = Invention.draft("ana", "Paraguas solar", Decimal("49.9"))

    assert invention.status is InventionStatus.DRAFT
    assert invention.price.amount == Decimal("49.90")


@pytest.mark.parametrize("amount", [Decimal("0"), Decimal("-1"), None])
def test_price_must_be_positive(amount):
    with pytest.raises(BusinessRuleViolation) as error:
        Price(amount)
    assert error.value.rule is Rule.PRICE_MUST_BE_POSITIVE


def test_name_is_required():
    with pytest.raises(BusinessRuleViolation) as error:
        Invention.draft("ana", "   ", Decimal("10"))
    assert error.value.rule is Rule.NAME_REQUIRED


def test_cannot_publish_without_image():
    invention = Invention.draft("ana", "Paraguas solar", Decimal("10"))

    with pytest.raises(BusinessRuleViolation) as error:
        invention.publish()
    assert error.value.rule is Rule.IMAGE_REQUIRED
    assert invention.status is InventionStatus.DRAFT


def test_publishes_once_it_has_an_image():
    invention = Invention.draft("ana", "Paraguas solar", Decimal("10"))
    invention.change_image("https://img.example/p.png")

    invention.publish()

    assert invention.status is InventionStatus.PUBLISHED


def test_cannot_publish_twice():
    invention = Invention.draft("ana", "Paraguas solar", Decimal("10"), "https://img.example/p.png")
    invention.publish()

    with pytest.raises(BusinessRuleViolation) as error:
        invention.publish()
    assert error.value.rule is Rule.ALREADY_PUBLISHED


def _draft(name: str) -> Invention:
    return Invention.draft("ana", name, Decimal("10"))


def _published(name: str) -> Invention:
    invention = Invention.draft("ana", name, Decimal("10"), "https://img.example/x.png")
    invention.publish()
    return invention


def test_name_is_unique_per_inventor_ignoring_case_and_spaces():
    with pytest.raises(BusinessRuleViolation) as error:
        check_can_create("  paraguas SOLAR ", [_published("Paraguas solar")])
    assert error.value.rule is Rule.NAME_ALREADY_USED


def test_at_most_three_drafts():
    with pytest.raises(BusinessRuleViolation) as error:
        check_can_create("D", [_draft("A"), _draft("B"), _draft("C")])
    assert error.value.rule is Rule.TOO_MANY_DRAFTS


def test_published_inventions_do_not_count_as_drafts():
    check_can_create("E", [_draft("A"), _draft("B"), _published("C"), _published("D")])
