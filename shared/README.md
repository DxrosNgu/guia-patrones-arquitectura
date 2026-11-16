# shared/ — archivos que usan varios labs

Lo que es **idéntico** en varios labs vive aquí una sola vez. Si un lab necesita una versión **distinta**, lleva su propia copia dentro del lab.

| Ruta | Qué es | Lo usan |
|---|---|---|
| [`docker/docker-entrypoint.sh`](docker/docker-entrypoint.sh) | Convención `*_FILE` para leer secretos desde archivos (como las imágenes oficiales de Postgres) | Lab 01 (`catalog` e `inventory`) |
| [`python/saga_kit/`](python/saga_kit) | Kit de mensajería para participantes Python de una saga: envelope, Transactional Outbox, Idempotent Consumer y consumidor de Kafka con DLQ | Labs 16 y 17 (`inventory` y `shipping`) |

Los infra de Compose (Postgres, Kafka…) también se comparten, pero viven en [`infra/`](../infra/) porque se usan de otra forma (`include:`).

## Cómo los usa un lab (sin copiarlos)

Compose le pasa a la build un **contexto adicional** llamado `shared` (requiere Docker Compose 2.17+):

```yaml
# labs/16-saga-coreografia/compose.yaml
inventory:
  build:
    context: ./services/inventory
    additional_contexts:
      shared: ../../shared
```

Y el Dockerfile copia desde ese contexto:

```dockerfile
COPY --from=shared python/saga_kit ./saga_kit
```

Así el servicio no necesita que su contexto de build sea la raíz del repo, y cada lab sigue siendo independiente.

## En tu máquina (tests y modo desarrollo)

Los servicios que usan el kit lo agregan al `pythonpath` de pytest en su `pyproject.toml`:

```toml
[tool.pytest.ini_options]
pythonpath = [".", "../../../../shared/python"]
```

El kit tiene sus propios tests:

```bash
cd shared/python && uv run pytest
```
