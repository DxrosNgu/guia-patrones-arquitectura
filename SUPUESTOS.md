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

- **L01-1 ✅ El lab tiene su propio Postgres** para mostrar `POSTGRES_PASSWORD_FILE`. **Respuesta:** OK, y los valores reales van al repo privado de credenciales. **Hecho:** el repo de credenciales tiene `secrets/*.txt` y los secretos de dev; este repo solo guarda los `*.example`.
- **L01-2 ✅ Tres formas de entregar secretos, una por ambiente.** **Respuesta:** te gusta; pediste el criterio. **Hecho:** el README del lab explica el porqué en "¿Por qué tres formas distintas y no una?" (quién entrega el secreto y qué riesgo hay en cada ambiente).
- **L01-3 ✅ Cada ambiente es un proyecto de Compose con puertos propios.** **Respuesta:** te gusta, porque permite probar ambientes cruzando puertos. Sin cambios.
- **L01-4 ✅ CORS.** **Respuesta:** hacer la demostración en prod. **Hecho:** en prod el catálogo se publica como API pública en otro origen (`:8201`) con `QUARKUS_HTTP_CORS_ORIGINS=http://localhost:3200`. La web tiene un botón que la llama, y un "otro sitio" en `:3299` muestra cómo el navegador lo bloquea. Hay tests de CORS en el catálogo, chequeos en `smoke.sh prod` y capturas en el README. Quarkus responde `403 CORS Rejected` a los orígenes no autorizados.
- **L01-5 ✅ Nivel de log.** **Respuesta:** que afecte a todo; DEBUG solo en dev y el resto en INFO, por seguridad. **Hecho:** `quarkus.log.level` (raíz) en Quarkus y logger raíz más uvicorn en Python. Local y prod en `INFO`, dev en `DEBUG`. `smoke.sh` verifica el nivel en cada ambiente.
- **L01-6 ✅ Contenedores sin root (uid 1001).** **Respuesta:** OK, considerando el repo de credenciales. Los archivos que se copian de ahí funcionan con permisos 644.
- **L01-7 ✅ `httpx2` para los tests de Python.** **Respuesta:** usar httpx2. Ya está así en todos los servicios.

### Lab 02 · Capas vs. Hexagonal vs. Clean

- **L02-1 🟢 Puertos 8111 y 8112** para las versiones hexagonales (Quarkus y Python). La convención reserva 8101 para `catalog`, que aquí usa la versión en capas.
- **L02-2 🟢 Las tres versiones comparten la base `catalog`**, cada una en su esquema (`layered`, `hexagonal`, `hexagonal_py`). Son el mismo servicio, así que no aplica Database per Service entre ellas.
- **L02-3 🟢 El mismo contrato HTTP en las tres** (JSON, códigos de error y estados), para que el smoke y los tests de API sean idénticos. Por eso la versión en capas también devuelve `{code, message}`, aunque lo "típico" sería un 400 genérico de Bean Validation.
- **L02-4 🟢 Clean y Onion no tienen versión propia.** Se explican con una tabla de vocabulario sobre la versión hexagonal: comparten la regla de dependencia y la diferencia es de nombres y de organización.
- **L02-5 🟢 El CLI solo existe en la versión Python.** Agregarlo a las otras queda como experimento para el lector.
- **L02-6 🟡 En memoria, la versión Quarkus sigue conectándose a Postgres al arrancar** (Flyway e Hibernate se inicializan siempre). Desactivarlos obligaba a cambiar el adaptador a `Instance<EntityManager>`. Lo dejé como ejercicio en lugar de complicar el código principal.
- **L02-7 🟢 "Nombre único" se comprueba leyendo antes de escribir**, sin índice único. Bajo concurrencia puede fallar, y el README lo marca con ⚠️.

### Lab 03 · DDD

- **L03-1 🟢 Un solo servicio con los dos modelos** (`/api/anemic/orders` y `/api/orders`), en lugar de dos servicios como en el lab 02. Así se comparan en el mismo proyecto y se ahorra un contenedor.
- **L03-2 🟡 El agregado lleva anotaciones de JPA** (atajo pragmático). Separarlas ya se enseña en el lab 02; aquí el foco son las invariantes. `Money`, `OrderLine` y `Quantity` son records (Hibernate los soporta como `@Embeddable`).
- **L03-3 🟢 La invariante "no se modifica después de pagado" quedó más estricta:** las líneas solo se editan en `DRAFT`. Ajusté el texto del catálogo.
- **L03-4 🟢 Agregué `@Version` y un experimento de concurrencia** que no estaba en el plan. El anémico pierde o duplica líneas en silencio y el agregado responde `409`. Es el argumento más claro para "un agregado = una unidad de consistencia".
- **L03-5 🟢 Los eventos de dominio son eventos CDI dentro del proceso**, con `AFTER_SUCCESS`. `/api/events` los guarda en memoria solo para verlos. La publicación entre servicios queda para los labs 06 y 15.
- **L03-6 🟢 El lado estratégico es documentación** (tabla de "Invento" por contexto y mapa de contextos en mermaid), sin código propio. Cada relación del mapa apunta al lab que la implementa.

### Lab 04 · Monolito modular

- **L04-1 🟢 Puerto 8100 para `shop`**, que no estaba en la convención: el monolito junta catálogo, pedidos, inventario y pagos.
- **L04-2 🟢 Agregué un módulo `shipping`** que escucha `OrderPlaced`. Sirve para mostrar eventos dentro del proceso sin ciclos de dependencia y la consistencia eventual dentro de un monolito (`SHIPPING_CHAOS_FAIL`).
- **L04-3 🟢 La versión ingenua es un experimento**, no un servicio aparte: `experiments/romper-limites.sh` agrega una clase que viola un límite y muestra cómo falla el build. Corre con Docker, o con `LOCAL=1` si tienes Java 25.
- **L04-4 🟡 Un solo usuario de base de datos para todos los esquemas.** ArchUnit no ve el SQL nativo que cruza esquemas. Un usuario por esquema obligaría a usar varios datasources y perder la transacción única. Lo explico en "Costo".
- **L04-5 🟢 Base `shop`** (la misma que usaba la versión ingenua del lab 05), con un esquema por módulo creado por Flyway.

### Lab 05 · Microservicios

- **L05-1 🟡 Sin `compose.dev.yaml` desde este lab.** **Preguntaste qué es un overlay.** Es un archivo de Compose que se **superpone** al `compose.yaml` base y solo cambia lo que difiere en un ambiente: `docker compose -f compose.yaml -f compose.prod.yaml up` combina los dos, y gana el último. En el lab 01 son `compose.override.yaml` (local, se carga solo), `compose.dev.yaml` y `compose.prod.yaml`. Dónde verlo: [`docs/ambientes.md`](docs/ambientes.md), el [README del lab 01](labs/01-ambientes/README.md#el-patrón) y el glosario.
  **La pregunta sigue abierta:** ¿quieres los tres overlays (local, dev, prod) en **todos** los labs aunque dev sea casi igual a local, o solo donde el patrón cambia entre ambientes (como ahora)?
- **L05-2 ✅ El pedido trae los precios en la petición.** **Pediste la explicación.** En la vida real, pedidos **no puede confiar** en el precio que manda el cliente: cualquiera podría comprar a 0,01 editando la petición. Debería pedírselo al catálogo (por API o con una copia local del precio actualizada por eventos). Este lab no tiene catálogo para no sumar un cuarto servicio que no aporta a Database per Service. Por eso está marcado con ⚠️: es una **simplificación consciente**, y el ⚠️ avisa que no se debe copiar así a un sistema real.
- **L05-3 ✅ El servicio `report` (equipo de BI).** **Pediste la explicación.** Representa a otro equipo que necesita datos de pedidos **y** de inventario juntos, por ejemplo "pedidos con el stock actual de cada producto".
  - **En la versión ingenua** hace un `JOIN` directo entre tablas de dos equipos. Es rápido de escribir, pero cuando inventario renombra una columna interna, el reporte se rompe sin que nadie toque su código (experimento 2).
  - **En la versión correcta** llama a las API de pedidos e inventario y une los datos en memoria. Es más lento y más trabajo, pero sobrevive a los cambios internos de cada equipo (experimento 3).

  Es el caso que justifica los labs 09 (API Composition) y 14 (CQRS).
- **L05-4 ✅ La "versión 2" de inventario se simula con `INVENTORY_V2=true`.** OK.
- **L05-5 ✅ Las lecciones como casos de despliegue.** **Hecho:** el README tiene una sección nueva, "Casos que pueden pasar durante un despliegue", con síntoma, causa, cómo reproducirlo y prevención:
  1. El rollback imposible, con la tabla de *expand/contract*.
  2. La petición que llega al servicio equivocado (IP reutilizada y caché de DNS).
  3. El healthcheck de Postgres que decía "listo" antes de tiempo, que apareció al reverificar los labs.

### Lab 06 · Sync vs. async

- **L06-1 ✅ Un solo servicio `orders` con `ORDERS_MODE=sync|async`.** OK.
- **L06-2 ✅ `aiokafka` para inventario.** **Respuesta:** hacer una comparativa con Quarkus (o con Spring si Quarkus no lo soportaba). **Hecho:** Quarkus lo soporta todo. Agregué `services/inventory-quarkus` (perfil `quarkus`), el mismo consumidor con Quarkus Messaging, más [`docs/consumidor-aiokafka-vs-quarkus.md`](labs/06-sync-vs-async/docs/consumidor-aiokafka-vs-quarkus.md) y `smoke-quarkus.sh`. El documento incluye la tabla equivalente de Spring Kafka. Lo que apareció al construirlo:
  - `@Retry` deja de reintentar a los 3 minutos si no pones `maxDuration = 0`.
  - `@Retry` no deja log entre intentos; se agregó un `BeforeRetryHandler`.
  - El conector de SmallRye usa `acks=1` por defecto (ver L06-5).
- **L06-3 ✅ Las réplicas extra son un servicio aparte (`inventory-worker`).** **Preguntaste si tenías que revisar algo.** No; es informativo. Con `--scale inventory=3` y un puerto publicado fijo, Compose no puede darles el mismo puerto a tres contenedores, y con un rango de puertos el puerto cambiaba en cada reinicio. Por eso las réplicas extra viven en un servicio sin puertos que solo consume eventos. El experimento de Competing Consumers funciona igual.
- **L06-4 ✅ Tests de pedidos con el conector en memoria.** OK, son solo para tests. El consumidor Quarkus nuevo prueba contra un Kafka real (Dev Services).
- **L06-5 ✅ Prod con un clúster KRaft de 3 nodos.** **Respuesta:** agregarlo como opcional por la RAM, explicando los requisitos. **Hecho:** `compose.prod.yaml` y `smoke-prod.sh`.
  - **RAM medida:** local ~1,1 GB, prod ~1,8 GB. Requisitos: Docker con ≥ 4 GB asignados y equipo con ≥ 8 GB (16 GB recomendado).
  - **Experimentos:**
    - cae 1 nodo y todo sigue;
    - caen 2 y aparece `NOT_ENOUGH_REPLICAS` con pérdida de quórum;
    - "el 500 que mintió": el pedido igual se procesa al volver los nodos;
    - `acks=1` acepta eventos que nadie puede leer.
  - **Corrección en el código:** pedidos ahora fija `acks=all`, porque el default de SmallRye es `acks=1`.

### Lab 10 · Observabilidad

- **L10-1 🟡 Sin gateway ni frontend en la traza.** **Pediste más detalle.** Hoy la traza **empieza en `orders`**: el primer span es `POST /api/orders`, porque la petición la hace `curl` o `requests.http` directamente. El plan original decía "de React al gateway, a pedidos, a Kafka, a inventario", pero el gateway (lab 07) y el frontend de ese flujo todavía no existen.
  - Mira la captura `labs/10-observabilidad/docs/traza.png`: arriba de todo está `orders POST /api/orders`, sin nada antes.
  - Cuando exista el lab 07, se puede sumar el gateway: Spring Cloud Gateway propaga el `traceparent` y la traza empezaría un nivel más arriba.
  - **Pendiente:** extender el lab 10 cuando exista el lab 07.
- **L10-2 ✅ Pedidos consulta el stock por HTTP antes de publicar.** Lo vas a revisar.
- **L10-3 ✅ En Python la telemetría se configura a mano.** **Respuesta:** hacer un ejemplo con Quarkus (o Spring si no lo soporta) y una comparativa en un `.md`. **Hecho:** Quarkus lo soporta. Pedidos ahora tiene:
  - un span con `@WithSpan` (`orders.check-stock`, en `StockCheck`);
  - un span creado con el `Tracer` (`orders.save-and-publish`, con `Scope`, evento y manejo de errores);
  - una métrica de negocio con el `Meter` (`orders_placed_total{result}`).

  Hay tests con un exportador en memoria, chequeos en `smoke.sh` y una captura nueva. La comparativa está en [`docs/instrumentacion-manual-vs-automatica.md`](labs/10-observabilidad/docs/instrumentacion-manual-vs-automatica.md): Python a mano, Python con `opentelemetry-instrument`, Quarkus, y una tabla de Spring Boot. También bajé la exportación de métricas de Quarkus de 60 s a 10 s.
- **L10-4 ✅ SQLAlchemy 2.0.57 en este lab.** **Respuesta:** dejarlo como actualización futura. Ya está en [`docs/actualizaciones-futuras.md`](docs/actualizaciones-futuras.md).

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
