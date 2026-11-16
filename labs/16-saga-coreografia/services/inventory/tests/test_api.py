from fastapi.testclient import TestClient
from sqlalchemy import create_engine, text

from app.main import Settings, create_app


def test_exposes_stock_and_health(database_url):
    engine = create_engine(database_url)
    with engine.begin() as conn:
        conn.execute(text("DROP TABLE IF EXISTS stock, reservations, cancellations, outbox, processed_messages"))
    engine.dispose()

    client = TestClient(create_app(Settings(database_url=database_url)))

    assert client.get("/health").json() == {"status": "UP"}
    assert {"sku": "INV-005", "available": 1} in client.get("/api/stock").json()
    assert client.get("/api/reservations").json() == []
