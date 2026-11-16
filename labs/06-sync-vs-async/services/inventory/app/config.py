from pydantic import Field
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    database_url: str = Field(min_length=1)
    # Sin bootstrap servers el consumidor no arranca (útil en tests y en modo solo-REST).
    kafka_bootstrap_servers: str | None = None
    kafka_topic: str = "orders.events"
    kafka_dlq_topic: str = "orders.events.dlq"
    # Todas las réplicas con el mismo group id se REPARTEN las particiones (Competing Consumers).
    kafka_group_id: str = "inventory"
    # Para eventos "notification": a dónde preguntar los ítems del pedido.
    orders_url: str = "http://localhost:8102"
    retry_backoff_seconds: float = 2.0
    log_level: str = "INFO"
