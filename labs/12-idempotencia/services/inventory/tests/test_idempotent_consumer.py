import json
from concurrent.futures import ThreadPoolExecutor

import pytest
from sqlalchemy import select

from app.events import InvalidEvent, OrderEventHandler, parse_event

EVENT = json.dumps({
    "eventId": "evt-1", "type": "OrderPlaced", "aggregateId": "o-1", "occurredAt": "2026-11-16T10:00:00Z",
    "payload": {"customerId": "ana", "items": [{"sku": "INV-001", "quantity": 2}]},
}).encode()


def stock_of(engine, tables, sku):
    stock = tables[0]
    with engine.connect() as conn:
        return conn.execute(select(stock.c.available).where(stock.c.sku == sku)).scalar()


def test_the_first_delivery_reserves(engine, tables):
    assert OrderEventHandler(engine, tables).handle(EVENT) == "RESERVED"
    assert stock_of(engine, tables, "INV-001") == 10


def test_a_duplicate_delivery_is_skipped(engine, tables):
    handler = OrderEventHandler(engine, tables)
    handler.handle(EVENT)

    assert handler.handle(EVENT) == "DUPLICATE"
    assert stock_of(engine, tables, "INV-001") == 10   # se descontó UNA sola vez


def test_without_idempotency_a_duplicate_reserves_twice(engine, tables):
    handler = OrderEventHandler(engine, tables, idempotent=False)
    handler.handle(EVENT)
    handler.handle(EVENT)

    assert stock_of(engine, tables, "INV-001") == 8    # ❌ doble reserva


def test_concurrent_duplicates_reserve_once(engine, tables):
    handler = OrderEventHandler(engine, tables)

    with ThreadPoolExecutor(max_workers=4) as pool:
        outcomes = list(pool.map(lambda _: handler.handle(EVENT), range(4)))

    assert sorted(outcomes) == ["DUPLICATE", "DUPLICATE", "DUPLICATE", "RESERVED"]
    assert stock_of(engine, tables, "INV-001") == 10


def test_a_rejected_event_is_also_remembered(engine, tables):
    rejected = EVENT.replace(b"INV-001", b"INV-002").replace(b"evt-1", b"evt-2")
    handler = OrderEventHandler(engine, tables)

    assert handler.handle(rejected) == "REJECTED"
    assert handler.handle(rejected) == "DUPLICATE"


def test_processed_messages_are_listed(client):
    assert client.get("/api/processed-messages").status_code == 200


@pytest.mark.parametrize("raw", [b"nope", b'{"type":"OrderPlaced"}'])
def test_malformed_events_are_invalid(raw):
    with pytest.raises(InvalidEvent):
        parse_event(raw)
