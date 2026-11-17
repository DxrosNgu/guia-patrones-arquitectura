from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    database_url: str = "postgresql+psycopg://legacy_shop:legacy_shop@localhost:5432/legacy_shop"
