"""Configuración del servicio.

Regla del lab: el código NO sabe en qué ambiente corre. Todo llega por variables
de entorno; pydantic-settings las lee y las VALIDA al arrancar. Si falta una
obligatoria, el servicio no levanta y el error dice cuál (fail fast).

    APP_ENV           → app_env          (obligatoria)
    SUPPLIER_API_KEY  → supplier_api_key (obligatoria, secreta)
    LOG_LEVEL         → log_level        (por defecto INFO)
    LOG_JSON          → log_json         (por defecto false)
"""

from pydantic import Field, SecretStr, field_validator
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    app_env: str = Field(min_length=1)
    supplier_api_key: SecretStr
    log_level: str = "INFO"
    log_json: bool = False

    @field_validator("supplier_api_key")
    @classmethod
    def not_empty(cls, value: SecretStr) -> SecretStr:
        if not value.get_secret_value():
            raise ValueError("supplier_api_key no puede estar vacía")
        return value

    def supplier_key_hint(self) -> str:
        """Los últimos 4 caracteres: suficiente para saber CUÁL clave está cargada."""
        return "****" + self.supplier_api_key.get_secret_value()[-4:]
