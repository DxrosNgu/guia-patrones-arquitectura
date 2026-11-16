import json

import pytest
from sqlalchemy import select

from app.events import InvalidEvent, OrderEventHandler, TransientError, parse_event


def event(type_="OrderPlaced", order_id="o-1", items=(("INV-001", 2),), with_items=True):
    payload = {"customerId": "ana"}
    if with_items:
        payload["items"] = [{"sku": s, "quantity": q} for s, q in items]
    return json.dumps({"eventId": "e-1", "type": type_, "aggregateId": order_id,
                       "occurredAt": "2026-11-16T10:00:00Z", "payload": payload}).encode()


# ── parse_event ──────────────────────────────────────────────────────────

def test_parses_a_valid_envelope():
    parsed = parse_event(event())

    assert parsed.type == "OrderPlaced"
    assert parsed.aggregate_id == "o-1"
    assert parsed.payload["items"][0]["sku"] == "INV-001"


@pytest.mark.parametrize("raw", [b"esto no es json", b"[]", b'{"type": "OrderPlaced"}', b"\xff\xfe"])
def test_rejects_malformed_messages(raw):
    with pytest.raises(InvalidEvent):
        parse_event(raw)


# ── OrderEventHandler ────────────────────────────────────────────────────

def stock_of(engine, tables, sku):
    stock, _ = tables
    with engine.connect() as conn:
        return conn.execute(select(stock.c.available).where(stock.c.sku == sku)).scalar()


def reservation_statuses(engine, tables):
    _, reservations = tables
    with engine.connect() as conn:
        return conn.execute(select(reservations.c.order_id, reservations.c.status)).all()


def test_order_placed_reserves_stock(engine, tables):
    outcome = OrderEventHandler(engine, tables, consumer_name="inv-a").handle(event())

    assert outcome == "RESERVED"
    assert stock_of(engine, tables, "INV-001") == 10
    assert reservation_statuses(engine, tables) == [("o-1", "RESERVED")]


def test_insufficient_stock_is_recorded_as_rejected(engine, tables):
    outcome = OrderEventHandler(engine, tables).handle(event(items=(("INV-002", 1),)))

    assert outcome == "REJECTED"
    assert reservation_statuses(engine, tables) == [("o-1", "REJECTED")]


def test_ignores_other_event_types(engine, tables):
    assert OrderEventHandler(engine, tables).handle(event(type_="OrderShipped")) == "IGNORED"


def test_notification_events_fetch_the_items_from_orders(engine, tables):
    calls = []

    def fetch_items(order_id):
        calls.append(order_id)
        return [{"sku": "INV-003", "quantity": 1}]

    outcome = OrderEventHandler(engine, tables, fetch_items=fetch_items).handle(event(with_items=False))

    assert outcome == "RESERVED"
    assert calls == ["o-1"]
    assert stock_of(engine, tables, "INV-003") == 4


def test_notification_events_fail_transiently_when_orders_is_down(engine, tables):
    def fetch_items(order_id):
        raise TransientError("orders no responde")

    with pytest.raises(TransientError):
        OrderEventHandler(engine, tables, fetch_items=fetch_items).handle(event(with_items=False))
