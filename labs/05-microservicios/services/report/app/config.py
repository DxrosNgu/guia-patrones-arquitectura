from typing import Literal

from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # api      → pregunta a orders e inventory por sus APIs (Database per Service)
    # database → lee directamente las tablas de otros servicios (versión ingenua)
    report_source: Literal["api", "database"] = "api"
    orders_url: str = "http://localhost:8102"
    inventory_url: str = "http://localhost:8103"
    database_url: str | None = None
