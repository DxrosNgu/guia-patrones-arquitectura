from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    orders_url: str = "http://localhost:8102"
    payments_url: str = "http://localhost:8104"
    shipping_url: str = "http://localhost:8105"
    # Tiempo máximo por llamada (segundos). Pasado este tiempo, ese dato queda "no disponible".
    call_timeout_s: float = 1.0
