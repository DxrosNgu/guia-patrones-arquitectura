import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, text
from testcontainers.community.postgres import PostgresContainer

from app.config import Settings
from app.main import create_app


@pytest.fixture(scope="module")
def database_url():
    with PostgresContainer("postgres:17.12-alpine", driver="psycopg") as postgres:
        yield postgres.get_connection_url()


@pytest.fixture
def client(database_url):
    engine = create_engine(database_url)
    with engine.begin() as conn:
        conn.execute(text("DROP TABLE IF EXISTS pedidos, productos; DROP SEQUENCE IF EXISTS pedidos_id_seq"))
    engine.dispose()
    return TestClient(create_app(Settings(database_url=database_url)))


def test_the_public_contract(client):
    assert client.get("/api/catalog/products/INV-001").json() == {"sku": "INV-001", "name": "Paraguas solar", "price": 49.9}
    created = client.post("/api/orders", json={"customer": "carla", "sku": "INV-004", "quantity": 2})
    assert created.status_code == 201
    assert created.headers["X-Served-By"] == "legacy-shop"
    order = created.json()
    assert order["id"] >= 1001 and order["total"] == 51.0
    assert client.get(f"/api/orders/{order['id']}").json() == order
    assert [o["id"] for o in client.get("/api/orders", params={"customer": "carla"}).json()] == [order["id"]]


def test_export_returns_only_orders_created_in_the_legacy(client):
    client.put("/internal/orders/500001", json={"customer": "ana", "sku": "INV-001", "quantity": 1,
                                                "total": "49.90", "createdAt": "2026-11-17T10:00:00Z"})

    exported = client.get("/internal/orders", params={"afterId": 0}).json()

    assert [o["id"] for o in exported] == [1001, 1002, 1003]
    assert [o["id"] for o in client.get("/internal/orders", params={"afterId": 1002}).json()] == [1003]


def test_import_is_idempotent_and_the_order_becomes_readable(client):
    body = {"customer": "ana", "sku": "INV-001", "quantity": 1, "total": "49.90", "createdAt": "2026-11-17T10:00:00Z"}
    assert client.put("/internal/orders/500002", json=body).status_code == 204
    assert client.put("/internal/orders/500002", json=body).status_code == 204

    assert client.get("/api/orders/500002").json()["customer"] == "ana"
    assert len([o for o in client.get("/api/orders", params={"customer": "ana"}).json() if o["id"] == 500002]) == 1
