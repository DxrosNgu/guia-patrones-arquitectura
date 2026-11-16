import pytest
from testcontainers.community.postgres import PostgresContainer


@pytest.fixture(scope="session")
def database_url():
    with PostgresContainer("postgres:17.12-alpine", driver="psycopg") as postgres:
        yield postgres.get_connection_url()
