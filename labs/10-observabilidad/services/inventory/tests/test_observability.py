import time

from fastapi.testclient import TestClient
from opentelemetry import trace
from sqlalchemy import create_engine

from app.config import Settings
from app.health import readiness
from app.main import create_app
from app.telemetry import context_from_kafka_headers, setup_telemetry

TRACEPARENT = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"


def test_liveness_only_says_the_process_is_alive(client):
    assert client.get("/health/live").json() == {"status": "UP"}


def test_readiness_checks_the_database(client):
    response = client.get("/health/ready")

    assert response.status_code == 200
    assert response.json()["checks"]["database"] == "UP"


def test_readiness_is_down_when_the_database_is_unreachable():
    broken = create_engine("postgresql+psycopg://x:x@127.0.0.1:1/x", connect_args={"connect_timeout": 1})

    status, checks = readiness(broken, consumer_alive=None)

    assert status == "DOWN"
    assert checks["database"].startswith("DOWN")


def test_readiness_reports_a_dead_kafka_consumer(engine):
    status, checks = readiness(engine, consumer_alive=False)

    assert status == "DOWN"
    assert checks["kafkaConsumer"] == "DOWN"


def test_telemetry_is_off_without_an_otlp_endpoint():
    assert setup_telemetry(Settings(database_url="postgresql+psycopg://x/x")) is False


def test_trace_context_travels_in_kafka_headers():
    context = context_from_kafka_headers([("traceparent", TRACEPARENT.encode())])

    span_context = trace.get_current_span(context).get_span_context()
    assert format(span_context.trace_id, "032x") == "0af7651916cd43dd8448eb211c80319c"


def test_artificial_delay_slows_down_stock_lookups(engine):
    settings = Settings(database_url=engine.url.render_as_string(hide_password=False), stock_lookup_delay_ms=300)
    client = TestClient(create_app(settings))

    started = time.perf_counter()
    client.get("/api/stock/INV-001")

    assert time.perf_counter() - started >= 0.3
