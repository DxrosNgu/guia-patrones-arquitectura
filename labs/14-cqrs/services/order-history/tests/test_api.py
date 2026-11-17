import threading
import time

from fastapi.testclient import TestClient

from app.api import create_app
from app.config import Settings
from app.projection import Projection
from tests.test_projection import paid, placed


def client_for(mongo_url, db):
    return TestClient(create_app(Settings(mongo_url=mongo_url, mongo_db=db.name)))


def test_history_with_filters(mongo_url, db):
    projection = Projection(db)
    projection.apply(placed("o-1", "e-1"))
    projection.apply(paid("o-1", "p-1"))
    client = client_for(mongo_url, db)

    body = client.get("/api/history", params={"customerId": "ana", "status": "PAGADO"}).json()

    assert [o["id"] for o in body] == ["o-1"]
    assert body[0]["payment"]["status"] == "CONFIRMED"


def test_read_your_writes_waits_until_the_view_has_the_version(mongo_url, db):
    client = client_for(mongo_url, db)

    def project_later():
        time.sleep(0.5)
        Projection(db).apply(placed("o-9", "e-9"))

    threading.Thread(target=project_later).start()
    start = time.perf_counter()
    response = client.get("/api/history/o-9", params={"minVersion": 1, "waitMs": 3000})

    assert response.status_code == 200
    assert 0.4 < time.perf_counter() - start < 2.5


def test_read_your_writes_gives_up_after_the_wait(mongo_url, db):
    response = client_for(mongo_url, db).get("/api/history/nope", params={"minVersion": 1, "waitMs": 300})

    assert response.status_code == 404
    assert response.json()["code"] == "NOT_YET_PROJECTED"
