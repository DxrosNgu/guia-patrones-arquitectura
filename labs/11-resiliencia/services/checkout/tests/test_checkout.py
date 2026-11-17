"""Checkout llama a pagos con tenacity: reintenta lo pasajero, con backoff exponencial y jitter."""
import asyncio
import json

import httpx
import pytest
from fastapi.testclient import TestClient

from app.main import create_app
from app.payments import PaymentsClient


def scripted(*responses):
    """Un pagos falso que responde, en orden, lo que se le indique (código HTTP o excepción)."""
    queue = list(responses)
    calls = []

    async def handler(request: httpx.Request) -> httpx.Response:
        calls.append(request)
        step = queue.pop(0) if len(queue) > 1 else queue[0]
        if isinstance(step, Exception):
            raise step
        return httpx.Response(step, content=json.dumps({"status": {201: "APPROVED", 202: "PENDING_CONFIRMATION",
                                                                   402: "DECLINED"}.get(step, "ERROR")}))

    return handler, calls


def client_for(handler, sleeps=None) -> PaymentsClient:
    async def fake_sleep(seconds: float):
        if sleeps is not None:
            sleeps.append(seconds)

    return PaymentsClient(httpx.AsyncClient(transport=httpx.MockTransport(handler)), "http://payments",
                          attempts=3, sleep=fake_sleep)


def pay(client: PaymentsClient):
    return asyncio.run(client.pay({"orderId": "o-1", "amount": 10}))


def test_success_on_the_first_attempt():
    handler, calls = scripted(201)
    response, attempts = pay(client_for(handler))
    assert (response.status_code, attempts, len(calls)) == (201, 1, 1)


def test_transient_errors_are_retried_with_growing_waits():
    sleeps = []
    handler, calls = scripted(503, httpx.ConnectError("caído"), 201)

    response, attempts = pay(client_for(handler, sleeps))

    assert (response.status_code, attempts, len(calls)) == (201, 3, 3)
    assert len(sleeps) == 2 and sleeps[1] > sleeps[0] * 1.2    # exponencial (con jitter)


def test_a_business_rejection_is_not_retried():
    handler, calls = scripted(402)
    response, attempts = pay(client_for(handler))
    assert (response.status_code, attempts, len(calls)) == (402, 1, 1)


def test_gives_up_after_the_last_attempt():
    handler, calls = scripted(httpx.ConnectError("caído"))
    with pytest.raises(httpx.ConnectError):
        pay(client_for(handler))
    assert len(calls) == 3


def test_endpoint_answers_503_when_payments_never_answers():
    handler, _ = scripted(httpx.ConnectError("caído"))
    api = TestClient(create_app(payments=client_for(handler)))

    response = api.post("/api/checkout", json={"orderId": "o-1", "amount": 10})

    assert response.status_code == 503
    assert response.json()["attempts"] == 3


def test_endpoint_relays_the_payment_and_how_many_attempts_it_took():
    handler, _ = scripted(503, 201)
    api = TestClient(create_app(payments=client_for(handler)))

    body = api.post("/api/checkout", json={"orderId": "o-1", "amount": 10}).json()

    assert (body["payment"]["status"], body["attempts"]) == ("APPROVED", 2)
