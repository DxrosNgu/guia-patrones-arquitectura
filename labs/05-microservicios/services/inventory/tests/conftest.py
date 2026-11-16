import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, text
from testcontainers.community.postgres import PostgresContainer

from app.config import Settings
from app.main import create_app


@pytest.fixture(scope="session")
def database_url():
    # Un Postgres real (el mismo motor que en prod), descartable, solo para los tests.
    with PostgresContainer("postgres:17.12-alpine", driver="psycopg") as postgres:
        yield postgres.get_connection_url()


@pytest.fixture
def clean_database(database_url):
    engine = create_engine(database_url)
    with engine.begin() as connection:
        connection.execute(text("DROP TABLE IF EXISTS reservations, stock CASCADE"))
    engine.dispose()
    return database_url


@pytest.fixture
def make_client(clean_database):
    def factory(**overrides):
        settings = Settings(database_url=clean_database, **overrides)
        return TestClient(create_app(settings))

    return factory


@pytest.fixture
def client(make_client):
    return make_client()
