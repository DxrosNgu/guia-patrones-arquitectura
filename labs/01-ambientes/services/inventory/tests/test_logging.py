import json
import logging

from app.logging_setup import JsonFormatter, configure_logging


def test_log_level_applies_to_the_app_logger_only():
    configure_logging("DEBUG", as_json=False)

    assert logging.getLogger("inventory").level == logging.DEBUG
    # Las librerías (uvicorn, asyncio...) se quedan en INFO: DEBUG es para NUESTRO código.
    assert logging.getLogger().level == logging.INFO


def test_json_formatter_produces_structured_entries():
    record = logging.LogRecord("inventory", logging.INFO, __file__, 1, "hola %s", ("mundo",), None)

    entry = json.loads(JsonFormatter().format(record))

    assert entry["level"] == "INFO"
    assert entry["logger"] == "inventory"
    assert entry["message"] == "hola mundo"
