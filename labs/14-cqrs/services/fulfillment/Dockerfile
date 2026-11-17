# syntax=docker/dockerfile:1
# Simulador de pagos y envíos: un proceso que consume y publica en Kafka (sin API HTTP).
FROM python:3.13.15-slim
COPY --from=astral/uv:0.11.32 /uv /bin/uv
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 UV_COMPILE_BYTECODE=1 UV_LINK_MODE=copy \
    UV_PYTHON_DOWNLOADS=never PATH="/app/.venv/bin:$PATH"
WORKDIR /app
COPY pyproject.toml uv.lock ./
RUN --mount=type=cache,target=/root/.cache/uv uv sync --locked --no-dev --no-install-project
COPY app ./app
RUN useradd --uid 1001 --no-create-home app
USER 1001
CMD ["python", "-m", "app.worker"]
