import pytest
from pymongo import MongoClient
from testcontainers.community.mongodb import MongoDbContainer


@pytest.fixture(scope="session")
def mongo_url():
    with MongoDbContainer("mongo:9.0.8") as mongo:
        yield mongo.get_connection_url()


@pytest.fixture
def db(mongo_url):
    client = MongoClient(mongo_url)
    database = client["history_test"]
    for name in database.list_collection_names():
        database.drop_collection(name)
    yield database
    client.close()
