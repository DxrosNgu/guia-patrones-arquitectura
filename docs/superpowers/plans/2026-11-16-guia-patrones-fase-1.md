# Guía de patrones de arquitectura — Plan de implementación (fase 1)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Construir la base del repositorio y los laboratorios ejecutables de la guía, empezando por la ruta "Saga a fondo".

**Architecture:** Monorepo con labs independientes (`labs/NN-nombre/`), cada uno con su propio `compose.yaml` y overlays por ambiente (`compose.override.yaml` = local, `compose.dev.yaml`, `compose.prod.yaml`). La infraestructura común vive en `infra/*.yaml` y se importa con `include:`. Cada servicio es un proyecto autocontenido: Quarkus (Maven) o FastAPI (pip + requirements.txt).

**Tech Stack:** Java 25 · Quarkus 3.40.1 · Python 3.13 · FastAPI 0.163 · React 19 + Vite 8 (JavaScript) · PostgreSQL 17 · Apache Kafka 4.1 (KRaft) · Redis 8 · Docker Compose ≥ 2.24.

**Spec:** `docs/superpowers/specs/2026-11-16-guia-patrones-design.md` (copia del README aprobado como borrador). Supuestos tomados sin el autor: `SUPUESTOS.md`.

## Global Constraints

- Quarkus por defecto en Java; Spring solo ante limitación real de Quarkus, documentada en el README del lab y en la tabla "Regla de stack".
- Cada lab arranca con `cp .env.example .env && docker compose up --build` sin pasos extra. El lab también debe arrancar **sin** `.env` (valores por defecto en `compose.yaml`).
- Código en inglés; documentación (README, comentarios didácticos) en español.
- Puertos según la convención de la sección 3.7 del README principal.
- Cada lab tiene README con las secciones: Problema · Versión ingenua · El patrón · Arquitectura del lab · Levántalo · Rómpelo · ¿Qué cambia en dev y prod? · Costo y cuándo no usarlo · Ejercicio · Referencias.
- Ningún secreto real en Git. `.env` en `.gitignore`; `.env.example` versionado.
- Versiones fijadas exactas en `pom.xml`, `requirements.txt`, `package.json` e imágenes Docker (nunca `latest`).

## Contratos compartidos (decididos aquí, usados por varios labs)

**Envelope de eventos (JSON en el value de Kafka, key = `aggregateId`):**

```json
{ "eventId": "uuid", "type": "OrderCreated", "aggregateId": "uuid",
  "occurredAt": "2026-11-16T10:00:00Z", "payload": { } }
```

**Topics:** `orders.events`, `inventory.events`, `payments.events`, `shipping.events`. Orquestación: `inventory.commands`, `payments.commands`, `shipping.commands`, `order-saga.replies`. DLQ: `<topic>.dlq`.

**Eventos:** `OrderCreated`, `OrderConfirmed`, `OrderApproved`, `OrderRejected`, `OrderCancelled` · `StockReserved`, `StockReservationFailed`, `StockReleased` · `PaymentCompleted`, `PaymentFailed`, `PaymentRefunded` · `ShipmentScheduled`, `ShipmentFailed`.

**Estados del pedido en sagas:** `PENDING → APPROVED | REJECTED | CANCELLED`.

**Bases de datos:** un contenedor Postgres (`infra/postgres.yaml`) que crea al iniciar una base y un usuario por servicio (`catalog`, `orders`, `inventory`, `payments`, `shipping`, `wallet`, `order_saga`, `order_history`, `legacy_shop`, `shop`), usuario = contraseña = nombre en local.

## Review Focus

- Lab levantado sin copiar `.env`: debe arrancar con defaults → cada `compose.yaml` usa `${VAR:-default}`.
- Lab levantado dos veces seguidas sin `down -v`: datos semilla no deben duplicarse → seeds idempotentes (`ON CONFLICT DO NOTHING`).
- Servicio que arranca antes que Postgres/Kafka: debe esperar → `depends_on: condition: service_healthy` + healthchecks en infra.
- Máquinas ARM (Mac M1/M2): imágenes multi-arch → solo imágenes oficiales multi-arch; nada de `platform:` forzado.
- Puertos ocupados por el lab anterior → README de cada lab recuerda `docker compose down -v`.

---

## Fase A — Base del repositorio

### Task A1: Esqueleto, documentación base y supuestos

**Files:** Create `README.md` (desde el spec, con estado actualizado), `SUPUESTOS.md`, `.gitignore`, `.editorconfig`, `docs/como-usar-la-guia.md`, `docs/ambientes.md`, `docs/plantilla-lab.md`, `docs/estilos.md`, `docs/glosario.md`.

- [ ] Escribir los archivos; `SUPUESTOS.md` lista cada decisión tomada sin el autor, con la pregunta concreta para aclarar.
- [ ] Commit `docs: base de la guía y supuestos` y push a `main`.

### Task A2: Infraestructura compartida

**Files:** Create `infra/postgres.yaml`, `infra/postgres/init/01-databases.sh`, `infra/kafka.yaml`, `infra/redis.yaml`, `infra/observability.yaml`, `infra/README.md`. (`mongodb.yaml`, `keycloak.yaml`, `consul.yaml`, `debezium.yaml` se crean con el primer lab que los use.)

**Produces:** servicios `postgres` (healthcheck `pg_isready`), `kafka` (KRaft, listener interno `kafka:9092`, healthcheck), `kafka-ui` (8090), `redis`, `lgtm`.

- [ ] `docker compose -f infra/postgres.yaml -f infra/kafka.yaml up -d` desde un compose de prueba; verificar `psql -U orders -d orders -c 'select 1'` y `kafka-topics.sh --list`.
- [ ] Commit `infra: bloques compartidos de compose`.

## Fase B — Ruta "Saga a fondo"

Cada task de lab sigue la misma secuencia:
1. Servicios con tests (Quarkus: `@QuarkusTest` + Dev Services; FastAPI: `pytest`).
2. `compose.yaml` + overlays + `.env.example` + `requests.http`.
3. README del lab con todas las secciones.
4. Verificación: tests verdes en host; imágenes construidas; `docker compose up -d`; script de humo del lab (`smoke.sh`) pasa; `docker compose down -v`.
5. Actualizar estado en el README principal, commit y push.

### Task B1: Lab 01 · Ambientes y configuración externalizada
`catalog` (Quarkus, 8101), `inventory` (FastAPI, 8103), `frontend` (React + nginx con `config.js` en runtime, 3000). Postgres propio del lab (para enseñar `secrets:`). Endpoint `GET /api/info` en cada servicio devuelve ambiente, nivel de log y feature flag. `smoke.sh` comprueba los tres ambientes.

### Task B2: Lab 05 · Microservicios y Database per Service
`orders` (Quarkus), `inventory` (FastAPI), `payments` (Quarkus), cada uno con su base. Perfil `naive`: mismos servicios contra base `shop` compartida + script que renombra columna y rompe `orders`.

### Task B3: Lab 06 · Comunicación síncrona vs. asíncrona
Variante `sync` (REST orders→inventory) y `async` (`OrderConfirmed` en Kafka). DLQ para mensaje malformado. Consumer group escalable.

### Task B4: Lab 10 · Observabilidad
OpenTelemetry en Quarkus (`quarkus-opentelemetry`) y FastAPI (`opentelemetry-instrumentation-*`), `grafana/otel-lgtm`. Health live/ready.

### Task B5: Lab 12 · Idempotencia
`Idempotency-Key` en `POST /payments`; tabla `processed_messages` en consumidor de inventory.

### Task B6: Lab 15 · Transactional Outbox
`orders` con tabla `outbox`; perfil `naive` con `CHAOS_CRASH_AFTER_COMMIT`; Polling Publisher (`@Scheduled`, `FOR UPDATE SKIP LOCKED`); variante Debezium (`quay.io/debezium/connect`).

### Task B7: Lab 16 · Saga por coreografía
Flujo de 5 pasos de la spec; controles de caos para S2–S13; UI React con línea de tiempo (SSE).

### Task B8: Lab 17 · Saga por orquestación
`order-saga` (Quarkus) con `saga_instance`; mismos casos.

## Fase C — Resto del catálogo

Labs 02, 03, 04, 07, 08, 09, 11, 13, 14, 18, 19, 20, 21, 22 en ese orden, mismo procedimiento.
