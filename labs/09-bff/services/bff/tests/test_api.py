from fastapi.testclient import TestClient

from app.main import create_app
from tests.test_compose import fake_services


def test_my_orders_endpoint():
    client = TestClient(create_app(services=fake_services()))
    body = client.get("/bff/my-orders", params={"customer": "ana"}).json()

    assert body["customer"] == "ana"
    assert body["mode"] == "parallel"
    assert len(body["orders"]) == 2
    assert body["tookMs"] >= 0


def test_orders_down_is_503():
    client = TestClient(create_app(services=fake_services(fail={"orders"})))
    assert client.get("/bff/my-orders", params={"customer": "ana"}).status_code == 503
