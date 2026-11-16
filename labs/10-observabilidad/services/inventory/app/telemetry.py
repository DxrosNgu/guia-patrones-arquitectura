"""OpenTelemetry para inventario: trazas, métricas y logs por OTLP.

Se configura "a mano" (en lugar de `opentelemetry-instrument`) para que se vea
cada pieza. En Quarkus, todo esto lo hace la extensión quarkus-opentelemetry.
"""

import logging

from opentelemetry import metrics, propagate, trace
from opentelemetry.context import Context
from opentelemetry.exporter.otlp.proto.http._log_exporter import OTLPLogExporter
from opentelemetry.exporter.otlp.proto.http.metric_exporter import OTLPMetricExporter
from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
from opentelemetry.instrumentation.logging import LoggingInstrumentor
from opentelemetry.sdk._logs import LoggerProvider, LoggingHandler
from opentelemetry.sdk._logs.export import BatchLogRecordProcessor
from opentelemetry.sdk.metrics import MeterProvider
from opentelemetry.sdk.metrics.export import PeriodicExportingMetricReader
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor

from app.config import Settings

tracer = trace.get_tracer("inventory")
meter = metrics.get_meter("inventory")

# Métrica de negocio: cuántas reservas, y con qué resultado.
reservations_counter = meter.create_counter(
    "inventory.reservations", unit="1", description="Reservas procesadas, por resultado")


def setup_telemetry(settings: Settings) -> bool:
    if not settings.otel_exporter_otlp_endpoint:
        return False
    endpoint = settings.otel_exporter_otlp_endpoint.rstrip("/")
    resource = Resource.create({"service.name": settings.otel_service_name})

    # Trazas
    tracer_provider = TracerProvider(resource=resource)
    tracer_provider.add_span_processor(BatchSpanProcessor(OTLPSpanExporter(endpoint=f"{endpoint}/v1/traces")))
    trace.set_tracer_provider(tracer_provider)

    # Métricas (se envían cada 10 s)
    reader = PeriodicExportingMetricReader(OTLPMetricExporter(endpoint=f"{endpoint}/v1/metrics"),
                                           export_interval_millis=10_000)
    metrics.set_meter_provider(MeterProvider(resource=resource, metric_readers=[reader]))

    # Logs: cada línea de `logging` también viaja a Loki, con su traceId.
    logger_provider = LoggerProvider(resource=resource)
    logger_provider.add_log_record_processor(BatchLogRecordProcessor(OTLPLogExporter(endpoint=f"{endpoint}/v1/logs")))
    logging.getLogger().addHandler(LoggingHandler(logger_provider=logger_provider))
    # Y el traceId también en la salida de consola.
    LoggingInstrumentor().instrument(set_logging_format=True)
    return True


def context_from_kafka_headers(headers) -> Context:
    """Recupera el contexto de la traza que orders puso en los headers del mensaje (traceparent)."""
    carrier = {key: value.decode() for key, value in (headers or []) if value is not None}
    return propagate.extract(carrier)
