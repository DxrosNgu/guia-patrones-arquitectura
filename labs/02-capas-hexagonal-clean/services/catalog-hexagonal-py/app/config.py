from typing import Literal

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    # Qué adaptador de persistencia se enchufa. El dominio no se entera.
    # Variable de entorno CATALOG_PERSISTENCE, igual que en la versión Quarkus.
    persistence: Literal["postgres", "memory"] = Field("postgres", validation_alias="CATALOG_PERSISTENCE")
    database_url: str | None = None
    log_level: str = "INFO"

    model_config = SettingsConfigDict(populate_by_name=True)
