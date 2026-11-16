"""Health Check API: dos preguntas distintas.

LIVENESS  "¿El proceso está vivo?"         Si dice DOWN, la plataforma lo REINICIA.
          Por eso NO revisa dependencias: si la BD cae, reiniciar inventario no la arregla
          (y reiniciar todos los servicios a la vez empeora todo).

READINESS "¿Puedo atender tráfico ahora?"  Si dice DOWN, la plataforma deja de mandarle
          peticiones, pero NO lo reinicia. Aquí sí se revisan las dependencias.
"""

from sqlalchemy import Engine, text


def readiness(engine: Engine, consumer_alive: bool | None) -> tuple[str, dict]:
    checks = {}
    try:
        with engine.connect() as conn:
            conn.execute(text("SELECT 1"))
        checks["database"] = "UP"
    except Exception as error:  # noqa: BLE001 — cualquier fallo de la BD cuenta como DOWN
        checks["database"] = f"DOWN ({type(error).__name__})"

    if consumer_alive is not None:
        checks["kafkaConsumer"] = "UP" if consumer_alive else "DOWN"

    status = "UP" if all(value == "UP" for value in checks.values()) else "DOWN"
    return status, checks
