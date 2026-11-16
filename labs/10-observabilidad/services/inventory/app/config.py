from pydantic import Field
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    database_url: str = Field(min_length=1)
    kafka_bootstrap_servers: str | None = None
    kafka_topic: str = "orders.events"
    kafka_dlq_topic: str = "orders.events.dlq"
    kafka_group_id: str = "inventory"
    retry_backoff_seconds: float = 2.0
    log_level: str = "INFO"

    # OpenTelemetry: mismas variables estándar que usa cualquier SDK de OTel.
    # Sin endpoint, la telemetría queda apagada (tests, modo desarrollo).
    otel_exporter_otlp_endpoint: str | None = None
    otel_service_name: str = "inventory"

    # Experimento: latencia artificial al consultar stock (para encontrarla en la traza).
    stock_lookup_delay_ms: int = 0
