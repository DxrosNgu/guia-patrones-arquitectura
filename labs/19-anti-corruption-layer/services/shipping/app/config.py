from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    legacy_carrier_url: str = "http://localhost:8121"
    carrier_timeout_s: float = 3.0
