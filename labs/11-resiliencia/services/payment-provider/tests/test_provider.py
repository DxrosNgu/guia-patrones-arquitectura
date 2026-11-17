"""La pasarela de pagos EXTERNA simulada: idempotente por clave, con controles de caos."""
from fastapi.testclient import TestClient

from app.main import create_app


def charge(client, key="k-1", amount=100.0):
    return client.post("/charges", json={"idempotencyKey": key, "orderId": "o-1", "amount": amount})


def test_charges_once_per_idempotency_key():
    client = TestClient(create_app())

    first = charge(client)
    again = charge(client)

    assert first.status_code == 201 and first.json()["status"] == "APPROVED"
    assert again.json()["chargeId"] == first.json()["chargeId"]
    assert again.json()["replayed"] is True
    assert client.get("/admin/stats").json() == {"requests": 2, "charges": 1, "rejected": 0}


def test_large_amounts_are_declined_a_business_error_not_a_failure():
    client = TestClient(create_app())

    response = charge(client, amount=5000)

    assert response.status_code == 402
    assert response.json()["status"] == "DECLINED"


def test_chaos_down_answers_503_and_counts_the_attempt():
    client = TestClient(create_app())
    client.put("/admin/chaos", json={"down": True})

    assert charge(client).status_code == 503
    assert client.get("/admin/stats").json()["requests"] == 1
    assert client.get("/admin/stats").json()["charges"] == 0


def test_chaos_error_rate_one_always_fails():
    client = TestClient(create_app())
    client.put("/admin/chaos", json={"errorRate": 1.0})

    assert charge(client).status_code == 500


def test_status_of_a_charge_can_be_queried_by_key():
    client = TestClient(create_app())
    charge(client, key="k-9")

    assert client.get("/charges/k-9").json()["status"] == "APPROVED"
    assert client.get("/charges/nope").status_code == 404


def test_reset_clears_everything():
    client = TestClient(create_app())
    charge(client)
    client.put("/admin/chaos", json={"down": True, "latencyMs": 500})

    client.post("/admin/reset")

    assert client.get("/admin/stats").json() == {"requests": 0, "charges": 0, "rejected": 0}
    assert client.get("/admin/chaos").json() == {"latencyMs": 0, "errorRate": 0.0, "down": False}
