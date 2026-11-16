"""Logs legibles para humanos (local) o JSON para máquinas (dev/prod).

En local lees los logs en la terminal. En dev/prod los lee un agregador
(Loki, Elasticsearch...) que necesita campos estructurados, no frases.
"""

import json
import logging
from datetime import datetime, timezone


class JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        entry = {
            "timestamp": datetime.fromtimestamp(record.created, timezone.utc).isoformat(),
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
        }
        if record.exc_info:
            entry["exception"] = self.formatException(record.exc_info)
        return json.dumps(entry, ensure_ascii=False)


def configure_logging(level: str, as_json: bool) -> None:
    handler = logging.StreamHandler()
    if as_json:
        handler.setFormatter(JsonFormatter())
    else:
        handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)-5s [%(name)s] %(message)s"))

    root = logging.getLogger()
    root.handlers = [handler]
    root.setLevel(logging.INFO)
    # El nivel configurable aplica a NUESTRO código; las librerías quedan en INFO.
    logging.getLogger("inventory").setLevel(level.upper())
