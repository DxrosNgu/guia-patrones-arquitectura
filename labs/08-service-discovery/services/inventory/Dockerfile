# syntax=docker/dockerfile:1
# Imagen del servicio con uv (se construye una vez y se usa en todos los ambientes).
FROM python:3.13.15-slim
COPY --from=astral/uv:0.11.32 /uv /bin/uv

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy \
    UV_PYTHON_DOWNLOADS=never \
    PATH="/app/.venv/bin:$PATH"

WORKDIR /app

# 1) Solo dependencias, exactamente las de uv.lock (capa cacheable: cambia poco).
COPY pyproject.toml uv.lock ./
RUN --mount=type=cache,target=/root/.cache/uv uv sync --locked --no-dev --no-install-project

# 2) El código (cambia seguido).
COPY app ./app

RUN useradd --uid 1001 --no-create-home app
USER 1001

EXPOSE 8000
HEALTHCHECK --interval=5s --timeout=3s --start-period=10s --retries=20 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8000/health')" || exit 1

CMD ["uvicorn", "app.main:create_app", "--factory", "--host", "0.0.0.0", "--port", "8000"]
