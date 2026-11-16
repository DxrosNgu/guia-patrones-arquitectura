# old/pip — la versión anterior de los servicios Python (con `pip`)

Los servicios Python de la guía usan **[uv](https://docs.astral.sh/uv/)**. Esta carpeta guarda cómo eran **con `pip`**: los mismos servicios, la misma estructura de carpetas (`old/pip/labs/<lab>/services/<servicio>/`) y solo los archivos que cambian.

Sirve para comparar, y para quien trabaje en un proyecto que todavía usa `pip`. **No se usa para levantar los labs.**

## Qué cambió en cada servicio

| Antes (pip) | Ahora (uv) | Para qué sirve |
|---|---|---|
| `requirements.txt` | `pyproject.toml` → `[project].dependencies` | Dependencias de la aplicación |
| `requirements-dev.txt` (con `-r requirements.txt`) | `pyproject.toml` → `[dependency-groups].dev` | Dependencias solo para desarrollo y tests |
| *(no había)* | `uv.lock` | Versiones **exactas** de **todo** el árbol de dependencias, con hashes |
| `pytest.ini` | `pyproject.toml` → `[tool.pytest.ini_options]` | Configuración de pytest |
| `Dockerfile` con `pip install -r requirements.txt` | `Dockerfile` con `uv sync --locked --no-dev` | Construir la imagen |

## Los comandos, lado a lado

| Tarea | pip | uv |
|---|---|---|
| Crear el entorno e instalar todo | `python -m venv .venv && . .venv/bin/activate && pip install -r requirements-dev.txt` | `uv sync` |
| Correr los tests | `pytest` (con el venv activado) | `uv run pytest` |
| Levantar el servicio | `uvicorn app.main:create_app --factory --reload` | `uv run uvicorn app.main:create_app --factory --reload` |
| Agregar una dependencia | Editar `requirements.txt` a mano y reinstalar | `uv add httpx` (actualiza `pyproject.toml` y `uv.lock`) |
| Agregar una dependencia de desarrollo | Editar `requirements-dev.txt` | `uv add --dev pytest-cov` |
| Actualizar una dependencia | Cambiar el pin y reinstalar | `uv lock --upgrade-package fastapi && uv sync` |
| Instalar en Docker exactamente lo probado | No garantizado (ver abajo) | `uv sync --locked` (falla si `uv.lock` no coincide con `pyproject.toml`) |

## Por qué el cambio

1. **Reproducibilidad real.** `requirements.txt` fijaba nuestras dependencias directas (`fastapi==0.163.1`), pero **no las suyas**. Si `starlette` publicaba una versión nueva, dos builds del mismo commit podían instalar cosas distintas. `uv.lock` fija el árbol **completo**, con hashes.
2. **Un solo archivo de proyecto.** Dependencias, grupos de desarrollo y configuración de herramientas (pytest) viven en `pyproject.toml`, el estándar de Python (PEP 621 y PEP 735).
3. **Velocidad.** uv resuelve e instala en segundos, y en Docker reutiliza su caché (`--mount=type=cache,target=/root/.cache/uv`).
4. **Sin activar entornos.** `uv run` usa el `.venv` del proyecto, y si no existe lo crea.

## Cómo se ve en el Dockerfile

**Antes (pip):**

```dockerfile
FROM python:3.13.15-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install -r requirements.txt          # resuelve versiones transitivas EN EL MOMENTO del build
COPY app ./app
CMD ["uvicorn", "app.main:create_app", "--factory", ...]
```

**Ahora (uv):**

```dockerfile
FROM python:3.13.15-slim
COPY --from=astral/uv:0.11.32 /uv /bin/uv    # el binario de uv, sin instalar nada más
ENV UV_COMPILE_BYTECODE=1 UV_LINK_MODE=copy UV_PYTHON_DOWNLOADS=never PATH="/app/.venv/bin:$PATH"
WORKDIR /app
COPY pyproject.toml uv.lock ./
RUN uv sync --locked --no-dev --no-install-project   # EXACTAMENTE lo que dice uv.lock
COPY app ./app
CMD ["uvicorn", "app.main:create_app", "--factory", ...]
```

- `--locked`: si alguien cambió `pyproject.toml` sin regenerar `uv.lock`, el build **falla** en lugar de instalar otra cosa.
- `--no-dev`: la imagen no lleva pytest ni testcontainers.
- `--no-install-project`: el servicio es una aplicación (`[tool.uv] package = false`), no una librería que haya que instalar.
- `UV_PYTHON_DOWNLOADS=never`: usa el Python de la imagen base; uv no descarga otro.

## ¿Y si mi proyecto usa pip?

Funciona, y no es un error. Para acercarte a la reproducibilidad de uv sin cambiar de herramienta, usa `pip-tools` (`pip-compile`) para generar un `requirements.txt` con **todas** las dependencias fijadas y sus hashes, e instala con `pip install --require-hashes -r requirements.txt`.
