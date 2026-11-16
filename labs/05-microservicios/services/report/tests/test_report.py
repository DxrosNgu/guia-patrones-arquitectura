import httpx
import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, text
from testcontainers.community.postgres import PostgresContainer

from app.config import Settings
from app.main import create_app
from app.sources import ApiSource, DatabaseSource, SourceError, compose_report

ORDERS = [
    {"id": "o1", "status": "CONFIRMED", "total": 99.8, "currency": "PEN",
     "lines": [{"sku": "INV-001", "quantity": 2, "unitPrice": 49.9}]},
]
STOCK = [{"sku": "INV-001", "available": 10}]


def test_compose_report_joins_orders_with_stock():
    report = compose_report(ORDERS, {"INV-001": 10})

    assert report == [{
        "orderId": "o1", "status": "CONFIRMED", "total": 99.8, "currency": "PEN",
        "lines": [{"sku": "INV-001", "quantity": 2, "stockAvailable": 10}],
    }]


def test_compose_report_marks_unknown_stock_as_none():
    assert compose_report(ORDERS, {})[0]["lines"][0]["stockAvailable"] is None


def test_api_source_uses_only_the_public_apis():
    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path == "/api/orders":
            return httpx.Response(200, json=ORDERS)
        if request.url.path == "/api/stock":
            return httpx.Response(200, json=STOCK)
        return httpx.Response(404)

    source = ApiSource("http://orders", "http://inventory", httpx.Client(transport=httpx.MockTransport(handler)))

    assert source.fetch()[0]["lines"][0]["stockAvailable"] == 10


def test_api_source_wraps_failures():
    source = ApiSource("http://orders", "http://inventory",
                       httpx.Client(transport=httpx.MockTransport(lambda r: httpx.Response(503))))

    with pytest.raises(SourceError):
        source.fetch()


@pytest.fixture(scope="module")
def shared_database():
    # La versión ingenua: TODAS las tablas en una misma base.
    with PostgresContainer("postgres:17.12-alpine", driver="psycopg") as postgres:
        engine = create_engine(postgres.get_connection_url())
        with engine.begin() as conn:
            conn.execute(text("""
                CREATE TABLE orders (id UUID PRIMARY KEY, status VARCHAR(10), total NUMERIC(12,2),
                                     currency CHAR(3), created_at TIMESTAMPTZ DEFAULT now());
                CREATE TABLE order_lines (id BIGINT PRIMARY KEY, order_id UUID, sku VARCHAR(20),
                                          quantity INT, unit_price NUMERIC(12,2));
                CREATE TABLE stock (sku VARCHAR(20) PRIMARY KEY, available INT);
                INSERT INTO orders VALUES ('11111111-1111-1111-1111-111111111111', 'CONFIRMED', 99.80, 'PEN');
                INSERT INTO order_lines VALUES (1, '11111111-1111-1111-1111-111111111111', 'INV-001', 2, 49.90);
                INSERT INTO stock VALUES ('INV-001', 10);
            """))
        yield engine
        engine.dispose()


def test_database_source_joins_tables_of_other_services(shared_database):
    report = DatabaseSource(shared_database).fetch()

    assert report[0]["lines"] == [{"sku": "INV-001", "quantity": 2, "stockAvailable": 10}]


def test_database_source_breaks_when_another_team_renames_a_column(shared_database):
    with shared_database.begin() as conn:
        conn.execute(text("ALTER TABLE stock RENAME COLUMN available TO units_available"))

    with pytest.raises(SourceError, match="available"):
        DatabaseSource(shared_database).fetch()


class FakeSource:
    name = "fake"

    def __init__(self, result=None, error=None):
        self.result, self.error = result, error

    def fetch(self):
        if self.error:
            raise self.error
        return self.result


def test_report_endpoint_returns_the_composed_report():
    client = TestClient(create_app(Settings(), source=FakeSource(result=[{"orderId": "o1"}])))

    response = client.get("/api/report")

    assert response.status_code == 200
    assert response.json() == {"source": "fake", "orders": [{"orderId": "o1"}]}


def test_report_endpoint_explains_failures_with_502():
    client = TestClient(create_app(Settings(), source=FakeSource(error=SourceError("column s.available does not exist"))))

    response = client.get("/api/report")

    assert response.status_code == 502
    assert "available" in response.json()["detail"]
