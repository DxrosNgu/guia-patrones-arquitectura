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


def payment_completed(order_id="o-1"):
    return new_event("PaymentCompleted", order_id, {"paymentId": "p-1", "amount": 99.8, "currency": "PEN",
                                                    "items": [{"sku": "INV-001", "quantity": 2}]})


def published(engine, tables):
    with engine.connect() as conn:
        return [json.loads(p) for p in conn.execute(select(tables.outbox.c.payload)
                                                    .order_by(tables.outbox.c.created_at)).scalars()]


def test_schedules_the_shipment_after_payment(setup):
    engine, tables, _, shipping = setup

    shipping.handle(payment_completed())

    [event] = published(engine, tables)
    assert event["type"] == "ShipmentScheduled"
    assert event["payload"]["trackingCode"].startswith("TRK-")


def test_a_definitive_rejection_triggers_the_compensations(setup):
    engine, tables, carrier, shipping = setup
    carrier.mode = "reject"

    shipping.handle(payment_completed())

    assert [e["type"] for e in published(engine, tables)] == ["ShipmentFailed"]


def test_a_carrier_outage_is_retried_not_compensated(setup):
    engine, tables, carrier, shipping = setup
    carrier.mode = "down"
    event = payment_completed()

    with pytest.raises(TransientError):
        shipping.handle(event)
    assert published(engine, tables) == []          # ni ShipmentFailed ni nada: se reintentará

    carrier.mode = "ok"
    shipping.handle(event)                          # el MISMO evento, reintentado
    assert [e["type"] for e in published(engine, tables)] == ["ShipmentScheduled"]


def test_duplicates_schedule_once(setup):
    engine, tables, _, shipping = setup
    event = payment_completed()

    shipping.handle(event)
    shipping.handle(event)

    assert len(published(engine, tables)) == 1


def test_a_known_cancellation_prevents_the_shipment(setup):
    engine, tables, _, shipping = setup

    shipping.handle(new_event("OrderCancelled", "o-1", {"reason": "cliente"}))
    shipping.handle(payment_completed())

    assert published(engine, tables) == []


def test_a_late_cancellation_cancels_the_scheduled_shipment(setup):
    engine, tables, _, shipping = setup
    shipping.handle(payment_completed())

    shipping.handle(new_event("OrderCancelled", "o-1", {"reason": "cliente"}))

    with engine.connect() as conn:
        assert conn.execute(select(tables.shipments.c.status)).scalar() == "CANCELLED"


def test_the_carrier_mode_can_be_changed_through_the_api(database_url):
    client = TestClient(create_app(Settings(database_url=database_url)))

    assert client.put("/api/carrier", json={"mode": "down"}).status_code == 200
    assert client.get("/api/carrier").json() == {"mode": "down"}
    assert client.put("/api/carrier", json={"mode": "volando"}).status_code == 422
