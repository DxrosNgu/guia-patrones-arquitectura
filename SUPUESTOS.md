# Supuestos y preguntas abiertas

Este archivo reúne las decisiones que tomé **sin poder consultarte** mientras construía la guía, con lo que me respondiste.

**Leyenda:**
- ✅ Resuelto con tu respuesta.
- 🟢 Decisión de bajo riesgo: cambiarla es barato.
- 🟡 Conviene que la revises: cambiarla toca varios labs.

---

## Decisiones generales (respondidas)

| # | Tema | Tu respuesta | Qué hice / haré |
|---|---|---|---|
| S-01 ✅ | Orden de construcción | Numérico, con todos los patrones | La ruta Saga (01 → 05 → 06 → 10 → 12 → 15 → 16 → 17) ya estaba hecha. El resto sigue en orden numérico: 02, 03, 04, 07, 08, 09, 11, 13, 14, 18 a 22 |
| S-02 ✅ | Archivos compartidos | En una carpeta general, salvo que sean otra versión | Carpeta `shared/` en la raíz. Los labs la usan con `additional_contexts` de Compose, sin copiarla. Lo que difiere entre labs (por ejemplo, el paquete Java de cada servicio) queda en cada lab |
| S-03 ✅ | Upgrade de tus servicios a Spring Boot | Aparte, con la última versión de Spring Boot | Fuera de esta guía. Queda anotado para el proyecto separado: última versión estable de Spring Boot |
| S-04 ✅ | Quarkus 4 | Solo releases. Pendiente opcional | Sigo en Quarkus 3.40.1 (estable). Quarkus 4 queda en [`docs/actualizaciones-futuras.md`](docs/actualizaciones-futuras.md) |
| S-05 ✅ | Spring Boot / Spring Cloud del gateway | Versión estable; opcional a futuro | Spring Boot 4.0.x + Spring Cloud 2025.1.x. Boot 4.1 + Cloud 2026.0 quedan en [`docs/actualizaciones-futuras.md`](docs/actualizaciones-futuras.md) |
| S-06 ✅ | Frontend | TypeScript | Todos los frontends en TypeScript (los labs 01 y 16 se migran) |
| S-07 ✅ | Python | `uv`; la versión con `pip` guardada como "old", con su explicación | Todos los servicios Python usan `uv` (`pyproject.toml` + `uv.lock`). La versión con `pip` queda en [`old/pip/`](old/pip/) con un README que explica las diferencias |
| S-08 ✅ | Idioma | Código en inglés, documentación en español | Sin cambios |
| S-09 ✅ | Kafka | KRaft; ZooKeeper como opcional con explicación | KRaft en todos los labs. Además, [`infra/optional/kafka-zookeeper.yaml`](infra/optional/kafka-zookeeper.yaml) y [`docs/kafka-kraft-vs-zookeeper.md`](docs/kafka-kraft-vs-zookeeper.md) |
| S-10 ✅ | Imágenes | OK | Sin cambios |
| S-11 ✅ | `.env` y secretos | OK; me los pasas por el chat | Al terminar te paso por el chat el contenido de los archivos que **no** se suben (`.env`, `secrets/*.txt`) |
| S-12 ✅ | Postgres compartido en local | OK, explicando que en prod van separados | Sin cambios (ver lab 05) |
| S-13 ✅ | Licencia | Sin licencia | Sin `LICENSE` |
| S-14 ✅ | Repos privados en el README | Son privados | Quité la sección que los nombraba |

## Decisiones por laboratorio

*(Las voy agregando a medida que construyo cada lab. Dijiste que las vas a revisar.)*

### Lab 01 · Ambientes

- **L01-1 🟢 El lab tiene su propio Postgres**, en lugar de usar `infra/postgres.yaml`. Así puede mostrar `POSTGRES_PASSWORD_FILE` con un secreto de prod. Los demás labs usan `infra/`.
- **L01-2 🟡 Tres formas de entregar secretos, una por ambiente:** valores falsos por defecto en local, variables obligatorias (`${VAR:?}`) en dev y archivos en `/run/secrets` en prod.
  - **Costo:** en dev hay que anteponer `DB_PASSWORD=… SUPPLIER_API_KEY=…` al comando, incluso para el `down`.
  - **Pregunta:** ¿te parece didáctico o prefieres que dev use archivos igual que prod?
- **L01-3 🟢 Cada ambiente es un proyecto de Compose distinto y puede correr a la vez que los otros.** Para eso, dev y prod usan otros puertos: web en 3100 y 3200, y servicios de dev en 8111 y 8113. Es una excepción a la convención de puertos, solo en este lab.
- **L01-4 🟢 Sin CORS.** El README general decía que entre ambientes cambiaban "los orígenes CORS". Al final, nginx hace de reverse proxy (`/api/catalog` y `/api/inventory`) y el navegador habla con un solo origen, así que no hace falta CORS. En su lugar varían Swagger y los puertos expuestos. Ya ajusté el README general.
- **L01-5 🟢 `LOG_LEVEL` solo afecta a los loggers de la app** (`com.mercadoinventos` en Quarkus, `inventory` en Python). Si se aplicara a todo, `DEBUG` inundaría la salida con logs de Hibernate y Vert.x.
- **L01-6 🟢 Los contenedores corren sin root (uid 1001).** Los archivos de `secrets/` tienen que ser legibles por ese usuario. Con los permisos normales (644) funciona.
- **L01-7 🟢 Para los tests de Python uso `httpx2`.** Es lo que pide Starlette ahora para su `TestClient`; con `httpx` muestra una advertencia de deprecación.

### Lab 05 · Microservicios

- **L05-1 🟡 Sin `compose.dev.yaml` desde este lab.** Solo incluyo los overlays en los que el patrón cambia de verdad entre ambientes. Aquí, prod muestra una instancia de base por servicio. Dev sería igual a local, y el lab 01 ya enseña esa diferencia.
  **Pregunta:** ¿quieres los tres overlays en todos los labs, aunque se repitan?
- **L05-2 🟢 El pedido trae los precios en la petición** para no necesitar el catálogo. Está marcado con ⚠️ en el README.
- **L05-3 🟢 Agregué un servicio `report` (equipo de BI)** que no estaba en el plan. Sirve para mostrar el acoplamiento por base compartida (JOIN directo) contra la composición por API.
- **L05-4 🟢 La "versión 2" de inventario se simula con `INVENTORY_V2=true`:** renombra una columna interna y mantiene el contrato de la API.
- **L05-5 🟢 Incluí dos lecciones que aparecieron mientras construía el lab:** el rollback imposible después de una migración destructiva (expand/contract) y un pedido mal enrutado por reutilización de IP más la caché DNS.

### Lab 06 · Sync vs. async

- **L06-1 🟢 Un solo servicio `orders` con un switch `ORDERS_MODE=sync|async`**, en lugar de dos servicios. Para cambiar de modo basta con recrearlo.
- **L06-2 🟢 Para inventario uso `aiokafka`**, en lugar de FastStream o confluent-kafka. Es Python puro y se ve todo el ciclo del consumidor (commit manual, DLQ, reintentos) sin magia.
- **L06-3 🟢 Las réplicas extra para Competing Consumers son un servicio aparte**, `inventory-worker` (perfil `scale`) y sin puertos. Con `--scale inventory=N` y puertos publicados, el puerto de inventario cambiaba al reiniciar.
- **L06-4 🟢 En pedidos, para los tests uso el conector en memoria de SmallRye**, sin un Kafka real. El Kafka real se prueba en `smoke.sh`.
- **L06-5 🟡 Sin overlay de prod:** simular 3 brokers pide ~1,5 GB más de RAM. El README lista los cambios de configuración de prod.
  **Pregunta:** ¿quieres un `compose.prod.yaml` con un clúster KRaft de 3 nodos de todos modos?

### Lab 10 · Observabilidad

- **L10-1 🟡 Sin gateway ni frontend en la traza.** El plan decía "de React a Kafka", pero el gateway es del lab 07 y todavía no existe. La traza empieza en `orders`. Cuando exista el lab 07, se puede sumar.
- **L10-2 🟢 Pedidos consulta el stock por HTTP antes de publicar.** Así la traza tiene HTTP **y** Kafka. Es un híbrido común en la vida real (validación rápida + procesamiento asíncrono).
- **L10-3 🟢 En Python, la telemetría se configura a mano** (sin `opentelemetry-instrument`) y el contexto de Kafka se extrae explícitamente. Así se ve cómo funciona la propagación.
- **L10-4 🟢 SQLAlchemy 2.0.57 en este lab:** la instrumentación de OpenTelemetry (0.66b2) todavía no soporta 2.1. Los demás labs siguen en 2.1.

### Lab 12 · Idempotencia

- **L12-1 🟢 No hay servicio de pedidos:** los eventos se publican con `publish-event.sh`. Así el lector controla exactamente cuándo se repite un evento.
- **L12-2 🟢 Concurrencia resuelta con la `PRIMARY KEY` + `INSERT … ON CONFLICT DO NOTHING`**, sin locks explícitos ni Redis. Es lo más simple que funciona con Postgres.
- **L12-3 🟢 Caos "muere antes del commit"** con un archivo marcador, para que se caiga **una sola** vez y no entre en un bucle de reinicios.

### Lab 15 · Transactional Outbox

- **L15-1 🟢 Un solo servicio con tres modos** (`OUTBOX_MODE=naive|polling|cdc`) para comparar dual write, polling y CDC con el mismo código de negocio.
- **L15-2 🟡 Debezium se conecta con el superusuario `admin`** para no tener que dar `REPLICATION` al usuario `orders` desde `infra/`. Está marcado con ⚠️ en el README.
  **Pregunta:** ¿prefieres un usuario de replicación dedicado, aunque complique `infra/postgres/init`?
- **L15-3 🟢 Debezium 3.6.3.Final desde `quay.io`** (gracias al permiso de red que me diste), con `snapshot.mode=no_data`. Así, al activar CDC después de usar polling, no se republican los eventos viejos.
- **L15-4 🟢 `wal_level=logical` va en `compose.override.yaml`.** Un archivo que incluye otro no puede redefinir sus servicios, pero un overlay sí puede modificarlos.

### Lab 16 · Saga por coreografía

- **L16-1 🟡 La UI consulta cada ~1 s en lugar de usar SSE**, como decía el plan. Es más simple, robusto y suficiente para ver la saga avanzar.
  **Pregunta:** ¿quieres que lo cambie a SSE (Server-Sent Events) para mostrar push real?
- **L16-2 🟢 Kit compartido para los participantes Python de los labs 16 y 17** ([`shared/python/saga_kit`](shared/python/saga_kit)), con outbox, inbox, envelope y consumidor. Los Dockerfiles lo copian con `COPY --from=shared` (S-02). En Java, las 4 clases equivalentes siguen dentro de cada servicio: cada copia tiene su propio paquete (`…orders.saga`, `…payments.saga`), así que cuentan como "otra versión".
- **L16-3 🟢 Cancelar solo se permite antes del pivote (el cobro).** Después responde `409`. Las carreras entre la cancelación y el cobro se resuelven recordando las cancelaciones en pagos, inventario y envíos.
- **L16-4 🟢 S6 (timeout del pago) y S13 (participante que nunca responde) quedan para el lab 17:** necesitan un coordinador que mida el tiempo de la saga.
- **L16-5 🟢 Agregué `INV-005` con 1 unidad** al stock inicial, para el caso S12.

### Lab 17 · Saga por orquestación

- **L17-1 🟢 El orquestador vive dentro de `orders`**, como en el libro de Richardson, y no en un servicio `order-saga` aparte (como decía el plan). Es un servicio menos y el flujo sigue en un solo archivo.
- **L17-2 🟢 Timeouts:** 5 s por paso y 3 intentos antes del pivote. Después del pivote y en las compensaciones se reintenta para siempre. Todo es configurable (`SAGA_STEP_TIMEOUT`, `SAGA_MAX_ATTEMPTS`).
- **L17-3 🟡 Temporal no está implementado:** solo aparece en el README como alternativa y ejercicio avanzado.
  **Pregunta:** ¿quieres una variante con Temporal?
- **L17-4 ✅ Frontends en TypeScript** (tu respuesta S-06): los de los labs 01, 16 y 17 ya están migrados.
