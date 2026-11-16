import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, text
from testcontainers.community.postgres import PostgresContainer

from app.config import Settings
from app.db import init_schema
from app.main import create_app


@pytest.fixture(scope="session")
def database_url():
    with PostgresContainer("postgres:17.12-alpine", driver="psycopg") as postgres:
        yield postgres.get_connection_url()


@pytest.fixture
def engine(database_url):
    engine = create_engine(database_url, pool_size=10)
    with engine.begin() as conn:
        conn.execute(text("DROP TABLE IF EXISTS processed_messages, reservations, stock CASCADE"))
    yield engine
    engine.dispose()


@pytest.fixture
def tables(engine):
    return init_schema(engine)


@pytest.fixture
def client(engine):
    settings = Settings(database_url=engine.url.render_as_string(hide_password=False))
    return TestClient(create_app(settings))
