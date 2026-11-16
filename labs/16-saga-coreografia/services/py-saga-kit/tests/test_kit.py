import json

import pytest
from sqlalchemy import MetaData, create_engine, select, text
from testcontainers.community.postgres import PostgresContainer

from saga_kit.envelope import InvalidEvent, new_event, parse_event
from saga_kit.inbox import first_time
from saga_kit.outbox import enqueue, publish_pending
from saga_kit.tables import kit_tables


@pytest.fixture(scope="module")
def engine():
    with PostgresContainer("postgres:17.12-alpine", driver="psycopg") as postgres:
        engine = create_engine(postgres.get_connection_url())
        yield engine
        engine.dispose()


@pytest.fixture
def tables(engine):
    with engine.begin() as conn:
        conn.execute(text("DROP TABLE IF EXISTS outbox, processed_messages"))
    metadata = MetaData()
    outbox, processed = kit_tables(metadata)
    metadata.create_all(engine)
    return outbox, processed


# ── envelope ─────────────────────────────────────────────────────────────

def test_new_event_has_the_common_envelope():
    event = new_event("StockReserved", "o-1", {"items": []})

    assert set(event) == {"eventId", "type", "aggregateId", "occurredAt", "payload"}
    assert event["aggregateId"] == "o-1"


@pytest.mark.parametrize("raw", [b"basura", b"[]", b'{"type":"X"}'])
def test_parse_rejects_malformed_messages(raw):
    with pytest.raises(InvalidEvent):
        parse_event(raw)


def test_parse_roundtrip():
    event = new_event("StockReserved", "o-1", {"x": 1})

    assert parse_event(json.dumps(event).encode()) == event


# ── inbox ────────────────────────────────────────────────────────────────

def test_first_time_is_true_only_once(engine, tables):
    _, processed = tables
    with engine.begin() as conn:
        assert first_time(conn, processed, "evt-1") is True
    with engine.begin() as conn:
        assert first_time(conn, processed, "evt-1") is False


# ── outbox ───────────────────────────────────────────────────────────────

def test_enqueued_events_are_published_once_and_marked(engine, tables):
    outbox, _ = tables
    event = new_event("StockReserved", "o-1", {})
    with engine.begin() as conn:
        enqueue(conn, outbox, "inventory.events", event)
    sent = []

    first = publish_pending(engine, outbox, lambda topic, key, value: sent.append((topic, key, value)))
    second = publish_pending(engine, outbox, lambda topic, key, value: sent.append((topic, key, value)))

    assert (first, second) == (1, 0)
    assert sent[0][0] == "inventory.events"
    assert sent[0][1] == "o-1"
    assert json.loads(sent[0][2])["eventId"] == event["eventId"]
    with engine.connect() as conn:
        assert conn.execute(select(outbox.c.published_at)).scalar() is not None


def test_a_failed_send_leaves_the_event_pending(engine, tables):
    outbox, _ = tables
    with engine.begin() as conn:
        enqueue(conn, outbox, "inventory.events", new_event("StockReserved", "o-1", {}))

    def broken(topic, key, value):
        raise ConnectionError("Kafka caído")

    with pytest.raises(ConnectionError):
        publish_pending(engine, outbox, broken)

    assert publish_pending(engine, outbox, lambda *args: None) == 1
