"""Inventario: cada instancia dice quién es y se registra sola en Consul (Self Registration)."""
import httpx
from fastapi.testclient import TestClient

from app.config import Settings
from app.consul import ConsulRegistry, registration
from app.main import create_app


def test_answers_stock_and_says_which_instance_answered():
    with TestClient(create_app(Settings(instance_name="inventory-a"))) as client:
        body = client.get("/api/stock/INV-001").json()

    assert body == {"sku": "INV-001", "available": 12, "instance": "inventory-a"}


def test_unknown_sku_is_404():
    with TestClient(create_app(Settings(instance_name="a"))) as client:
        assert client.get("/api/stock/NOPE").status_code == 404


def test_an_instance_can_be_made_sick_without_killing_it():
    with TestClient(create_app(Settings(instance_name="a"))) as client:
        assert client.get("/health").status_code == 200
        client.post("/admin/health", json={"healthy": False})
        assert client.get("/health").status_code == 503
        # Sigue respondiendo stock: está "enferma" para el registry, pero el proceso vive.
        assert client.get("/api/stock/INV-001").status_code == 200


def test_registration_includes_a_health_check_and_auto_deregistration():
    body = registration(service="inventory", instance="inventory-a", address="10.0.0.7", port=8000)

    assert body["ID"] == "inventory-a"
    assert body["Name"] == "inventory"
    assert (body["Address"], body["Port"]) == ("10.0.0.7", 8000)
    assert body["Check"]["HTTP"] == "http://10.0.0.7:8000/health"
    assert body["Check"]["DeregisterCriticalServiceAfter"]


def test_registers_on_startup_and_deregisters_on_shutdown():
    calls = []

    def consul(request: httpx.Request) -> httpx.Response:
        calls.append((request.method, request.url.path))
        return httpx.Response(200)

    registry = ConsulRegistry("http://consul:8500", transport=httpx.MockTransport(consul))
    settings = Settings(instance_name="inventory-a", consul_url="http://consul:8500", advertise_address="10.0.0.7")
    with TestClient(create_app(settings, registry=registry)):
        assert calls == [("PUT", "/v1/agent/service/register")]

    assert calls[-1] == ("PUT", "/v1/agent/service/deregister/inventory-a")
