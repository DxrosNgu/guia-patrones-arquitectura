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


def reserve(order_id="o-1", items=ITEMS):
    return new_event("ReserveStock", order_id, {"items": items})


def stock(engine, tables, sku):
    with engine.connect() as conn:
        return conn.execute(select(tables.stock.c.available).where(tables.stock.c.sku == sku)).scalar()


def replies(engine, tables):
    with engine.connect() as conn:
        rows = conn.execute(select(tables.outbox.c.topic, tables.outbox.c.payload)
                            .order_by(tables.outbox.c.created_at)).all()
    assert all(topic == "saga.replies" for topic, _ in rows)
    return [json.loads(p)["type"] for _, p in rows]


def test_reserve_stock_command_replies_stock_reserved(setup):
    engine, tables, inventory = setup

    inventory.handle(reserve())

    assert stock(engine, tables, "INV-001") == 10
    assert replies(engine, tables) == ["StockReserved"]


def test_without_stock_replies_the_failure(setup):
    engine, tables, inventory = setup
    inventory.handle(reserve(items=[{"sku": "INV-002", "quantity": 1}]))
    assert replies(engine, tables) == ["StockReservationFailed"]


def test_release_stock_compensates(setup):
    engine, tables, inventory = setup
    inventory.handle(reserve())

    inventory.handle(new_event("ReleaseStock", "o-1", {}))

    assert stock(engine, tables, "INV-001") == 12
    assert replies(engine, tables) == ["StockReserved", "StockReleased"]


def test_release_always_replies_even_if_there_was_nothing_to_release(setup):
    # El orquestador necesita la respuesta para dar la compensación por terminada.
    engine, tables, inventory = setup

    inventory.handle(new_event("ReleaseStock", "o-1", {}))

    assert replies(engine, tables) == ["StockReleased"]


def test_a_resent_reserve_command_does_not_reserve_twice(setup):
    # El orquestador reenvía el comando si no recibe respuesta (con OTRO eventId).
    engine, tables, inventory = setup

    inventory.handle(reserve())
    inventory.handle(reserve())

    assert stock(engine, tables, "INV-001") == 10
    assert replies(engine, tables) == ["StockReserved", "StockReserved"]


def test_two_sagas_compete_for_the_last_unit(setup):
    engine, tables, inventory = setup
    unit = [{"sku": "INV-005", "quantity": 1}]

    with ThreadPoolExecutor(max_workers=2) as pool:
        list(pool.map(inventory.handle, [reserve("o-a", unit), reserve("o-b", unit)]))

    assert sorted(replies(engine, tables)) == ["StockReservationFailed", "StockReserved"]
