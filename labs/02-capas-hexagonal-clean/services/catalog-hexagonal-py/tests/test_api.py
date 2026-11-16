"""El contrato HTTP: el mismo que en catalog-layered y catalog-hexagonal (Quarkus).

Corre dos veces: con el adaptador en memoria y con Postgres (Testcontainers).
"""
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app


@pytest.fixture(params=["memory", "postgres"])
def client(request):
    if request.param == "memory":
        settings = Settings(persistence="memory")
    else:
        settings = Settings(persistence="postgres", database_url=request.getfixturevalue("database_url"))
    with TestClient(create_app(settings)) as client:
        yield client


def _create(client, inventor, name, price, image=None):
    response = client.post("/api/inventions",
                           json={"inventorId": inventor, "name": name, "price": price, "imageUrl": image})
    assert response.status_code == 201, response.text
    assert response.json()["status"] == "DRAFT"
    return response.json()["id"]


def test_publish_flow(client):
    inventor = f"inv-{uuid4()}"
    invention_id = _create(client, inventor, "Paraguas solar", 49.9)

    response = client.post(f"/api/inventions/{invention_id}/publish")
    assert (response.status_code, response.json()["code"]) == (422, "IMAGE_REQUIRED")

    assert client.put(f"/api/inventions/{invention_id}/image",
                      json={"imageUrl": "https://img.example/p.png"}).status_code == 200

    response = client.post(f"/api/inventions/{invention_id}/publish")
    assert response.status_code == 200
    assert response.json()["status"] == "PUBLISHED"
    assert response.json()["price"] == 49.9

    assert len(client.get("/api/inventions", params={"inventorId": inventor}).json()) == 1


def test_business_rules_are_reported_with_a_code(client):
    inventor = f"inv-{uuid4()}"
    response = client.post("/api/inventions", json={"inventorId": inventor, "name": "Gratis", "price": 0})
    assert (response.status_code, response.json()["code"]) == (422, "PRICE_MUST_BE_POSITIVE")

    _create(client, inventor, "A", 1)
    response = client.post("/api/inventions", json={"inventorId": inventor, "name": " a ", "price": 1})
    assert (response.status_code, response.json()["code"]) == (409, "NAME_ALREADY_USED")

    _create(client, inventor, "B", 1)
    _create(client, inventor, "C", 1)
    response = client.post("/api/inventions", json={"inventorId": inventor, "name": "D", "price": 1})
    assert (response.status_code, response.json()["code"]) == (409, "TOO_MANY_DRAFTS")


def test_unknown_invention_is_404(client):
    assert client.get(f"/api/inventions/{uuid4()}").status_code == 404


def test_info(client):
    info = client.get("/api/info").json()
    assert info["architecture"] == "hexagonal-py"
    assert info["persistence"] in {"memory", "postgres"}
