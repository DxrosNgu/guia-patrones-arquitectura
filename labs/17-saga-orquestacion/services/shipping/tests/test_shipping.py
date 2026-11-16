import json

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, select, text

from app.carrier import Carrier
from app.db import init_schema
from app.main import Settings, create_app
from app.saga import ShippingParticipant
from saga_kit.envelope import TransientError, new_event


@pytest.fixture
def setup(database_url):
    engine = create_engine(database_url)
    with engine.begin() as conn:
        conn.execute(text("DROP TABLE IF EXISTS shipments, cancellations, outbox, processed_messages"))
    tables = init_schema(engine)
    carrier = Carrier()
    yield engine, tables, carrier, ShippingParticipant(engine, tables, carrier)
    engine.dispose()


def schedule(order_id="o-1"):
    return new_event("ScheduleShipment", order_id, {"items": [{"sku": "INV-001", "quantity": 1}]})


def replies(engine, tables):
    with engine.connect() as conn:
        rows = conn.execute(select(tables.outbox.c.topic, tables.outbox.c.payload)
                            .order_by(tables.outbox.c.created_at)).all()
    assert all(topic == "saga.replies" for topic, _ in rows)
    return [json.loads(p) for _, p in rows]


def test_schedule_command_replies_with_the_tracking_code(setup):
    engine, tables, _, shipping = setup

    shipping.handle(schedule())

    [reply] = replies(engine, tables)
    assert reply["type"] == "ShipmentScheduled"
    assert reply["payload"]["trackingCode"].startswith("TRK-")


def test_a_definitive_rejection_is_replied(setup):
    engine, tables, carrier, shipping = setup
    carrier.mode = "reject"

    shipping.handle(schedule())

    assert [r["type"] for r in replies(engine, tables)] == ["ShipmentFailed"]


def test_a_carrier_outage_is_retried(setup):
    engine, tables, carrier, shipping = setup
    carrier.mode = "down"
    command = schedule()

    with pytest.raises(TransientError):
        shipping.handle(command)

    carrier.mode = "ok"
    shipping.handle(command)
    assert [r["type"] for r in replies(engine, tables)] == ["ShipmentScheduled"]


def test_a_resent_command_returns_the_same_shipment(setup):
    engine, tables, _, shipping = setup

    shipping.handle(schedule())
    shipping.handle(schedule())        # reenviado por el orquestador (otro eventId)

    first, second = replies(engine, tables)
    assert first["payload"]["trackingCode"] == second["payload"]["trackingCode"]


def test_the_carrier_mode_can_be_changed_through_the_api(database_url):
    client = TestClient(create_app(Settings(database_url=database_url)))

    assert client.put("/api/carrier", json={"mode": "down"}).json() == {"mode": "down"}
    assert client.put("/api/carrier", json={"mode": "volando"}).status_code == 422
