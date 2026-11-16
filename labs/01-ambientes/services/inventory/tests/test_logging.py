import json
import logging

from app.logging_setup import JsonFormatter, configure_logging


def test_log_level_applies_to_everything():
    configure_logging("DEBUG", as_json=False)

    # El nivel aplica a todo: nuestro código y las librerías (uvicorn, SQLAlchemy...).
    assert logging.getLogger().level == logging.DEBUG
    assert logging.getLogger("inventory").getEffectiveLevel() == logging.DEBUG
    assert logging.getLogger("uvicorn.access").level == logging.DEBUG


def test_info_hides_debug_messages_everywhere():
    configure_logging("INFO", as_json=False)

    assert not logging.getLogger("sqlalchemy.engine").isEnabledFor(logging.DEBUG)
    assert not logging.getLogger("inventory").isEnabledFor(logging.DEBUG)


def test_json_formatter_produces_structured_entries():
    record = logging.LogRecord("inventory", logging.INFO, __file__, 1, "hola %s", ("mundo",), None)

    entry = json.loads(JsonFormatter().format(record))

    assert entry["level"] == "INFO"
    assert entry["logger"] == "inventory"
    assert entry["message"] == "hola mundo"
