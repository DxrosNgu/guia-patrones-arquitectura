import json
from concurrent.futures import ThreadPoolExecutor

import pytest
from sqlalchemy import create_engine, select, text

from app.db import init_schema
from app.saga import InventoryParticipant
from saga_kit.envelope import new_event

ITEMS = [{"sku": "INV-001", "quantity": 2, "unitPrice": 49.9}]


@pytest.fixture
def setup(database_url):
    engine = create_engine(database_url, pool_size=10)
    with engine.begin() as conn:
        conn.execute(text("DROP TABLE IF EXISTS stock, reservations, cancellations, outbox, processed_messages"))
    tables = init_schema(engine)
    yield engine, tables, InventoryParticipant(engine, tables)
    engine.dispose()


def order_created(order_id="o-1", items=ITEMS):
    return new_event("OrderCreated", order_id,
                     {"customerId": "ana", "items": items, "total": 99.8, "currency": "PEN"})


def stock(engine, tables, sku):
    with engine.connect() as conn:
        return conn.execute(select(tables.stock.c.available).where(tables.stock.c.sku == sku)).scalar()


def published(engine, tables):
    with engine.connect() as conn:
        return [json.loads(p)["type"] for p in conn.execute(
            select(tables.outbox.c.payload).order_by(tables.outbox.c.created_at)).scalars()]


def test_reserves_stock_and_announces_it(setup):
    engine, tables, inventory = setup

    inventory.handle(order_created())

    assert stock(engine, tables, "INV-001") == 10
    assert published(engine, tables) == ["StockReserved"]


def test_stock_reserved_carries_what_payments_needs(setup):
    engine, tables, inventory = setup
    inventory.handle(order_created())

    with engine.connect() as conn:
        payload = json.loads(conn.execute(select(tables.outbox.c.payload)).scalar())["payload"]
    assert payload["total"] == 99.8 and payload["customerId"] == "ana" and payload["items"] == ITEMS


def test_without_stock_announces_the_failure(setup):
    engine, tables, inventory = setup

    inventory.handle(order_created(items=[{"sku": "INV-002", "quantity": 1, "unitPrice": 35}]))

    assert published(engine, tables) == ["StockReservationFailed"]


def test_payment_failed_releases_the_stock(setup):
    engine, tables, inventory = setup
    inventory.handle(order_created())

    inventory.handle(new_event("PaymentFailed", "o-1", {"reason": "límite"}))

    assert stock(engine, tables, "INV-001") == 12
    assert published(engine, tables) == ["StockReserved", "StockReleased"]


def test_compensation_is_idempotent(setup):
    engine, tables, inventory = setup
    inventory.handle(order_created())

    inventory.handle(new_event("PaymentFailed", "o-1", {"reason": "límite"}))
    inventory.handle(new_event("ShipmentFailed", "o-1", {"reason": "otra"}))

    assert stock(engine, tables, "INV-001") == 12            # se liberó UNA vez
    assert published(engine, tables).count("StockReleased") == 1


def test_a_duplicate_event_is_processed_once(setup):
    engine, tables, inventory = setup
    event = order_created()

    inventory.handle(event)
    inventory.handle(event)

    assert stock(engine, tables, "INV-001") == 10


def test_a_cancellation_that_arrives_first_blocks_the_reservation(setup):
    engine, tables, inventory = setup

    inventory.handle(new_event("OrderCancelled", "o-1", {"reason": "el cliente se arrepintió"}))
    inventory.handle(order_created())

    assert stock(engine, tables, "INV-001") == 12
    assert published(engine, tables) == []


def test_two_sagas_compete_for_the_last_unit(setup):
    engine, tables, inventory = setup
    last_unit = [{"sku": "INV-005", "quantity": 1, "unitPrice": 999}]

    with ThreadPoolExecutor(max_workers=2) as pool:
        list(pool.map(inventory.handle, [order_created("o-a", last_unit), order_created("o-b", last_unit)]))

    assert sorted(published(engine, tables)) == ["StockReservationFailed", "StockReserved"]
    assert stock(engine, tables, "INV-005") == 0
