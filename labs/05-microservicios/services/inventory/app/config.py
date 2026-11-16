from pydantic import Field
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # Cada servicio tiene SU base. Ejemplo: postgresql+psycopg://inventory:inventory@postgres:5432/inventory
    database_url: str = Field(min_length=1)
    # Simula que el equipo de inventario despliega su versión 2, que renombra
    # una columna INTERNA. Ver "Rómpelo" en el README del lab.
    inventory_v2: bool = False
    log_level: str = "INFO"
