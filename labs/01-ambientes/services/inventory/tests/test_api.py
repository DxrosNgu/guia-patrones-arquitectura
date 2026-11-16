import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app

SECRET = "sk-supplier-abcd1234wxyz"


@pytest.fixture
def client():
    settings = Settings(app_env="test", supplier_api_key=SECRET)
    return TestClient(create_app(settings))


def test_info_reports_environment(client):
    response = client.get("/api/info")

    assert response.status_code == 200
    body = response.json()
    assert body["service"] == "inventory"
    assert body["environment"] == "test"
    assert body["logLevel"] == "INFO"


def test_info_never_exposes_the_full_secret(client):
    response = client.get("/api/info")

    assert response.json()["supplierKeyHint"] == "****wxyz"
    assert SECRET not in response.text


def test_lists_seeded_stock(client):
    response = client.get("/api/stock")

    assert response.status_code == 200
    stock = {item["sku"]: item["available"] for item in response.json()}
    assert stock["INV-001"] == 12
    assert stock["INV-002"] == 0


def test_stock_of_one_sku(client):
    response = client.get("/api/stock/INV-003")

    assert response.status_code == 200
    assert response.json() == {"sku": "INV-003", "available": 5}


def test_unknown_sku_returns_404(client):
    response = client.get("/api/stock/NOPE")

    assert response.status_code == 404


def test_health(client):
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "UP"}
