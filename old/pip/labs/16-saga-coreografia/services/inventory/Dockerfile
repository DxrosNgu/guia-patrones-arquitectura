# syntax=docker/dockerfile:1
# Contexto de build: services/ (para poder copiar el kit compartido py-saga-kit).
FROM python:3.13.15-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app
COPY inventory/requirements.txt .
RUN --mount=type=cache,target=/root/.cache/pip pip install -r requirements.txt

COPY py-saga-kit/saga_kit ./saga_kit
COPY inventory/app ./app

RUN useradd --uid 1001 --no-create-home app
USER 1001

EXPOSE 8000
HEALTHCHECK --interval=5s --timeout=3s --start-period=10s --retries=20 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8000/health')" || exit 1

CMD ["uvicorn", "app.main:create_app", "--factory", "--host", "0.0.0.0", "--port", "8000"]
