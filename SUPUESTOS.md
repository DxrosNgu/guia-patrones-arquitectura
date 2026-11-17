# Supuestos y preguntas abiertas

Este archivo reúne las decisiones que tomé **sin poder consultarte** mientras construía la guía, con lo que me respondiste.

**Leyenda:**
- ✅ Resuelto con tu respuesta.
- 🟢 Decisión de bajo riesgo: cambiarla es barato.
- 🟡 (el círculo amarillo) Conviene que la revises: es una decisión con un costo o un riesgo que acepté **a propósito**, o cambiarla toca varios labs. No es un error pendiente. Si trae **Pregunta:**, necesito tu respuesta.

En los README aparece otro símbolo, ⚠️: marca una **simplificación consciente**. Funciona en el lab, pero no debe copiarse así a producción, y el README explica cómo sería la versión real. Un 🟡 de aquí suele tener su ⚠️ en el README del lab.

---

## Decisiones generales (respondidas)

| # | Tema | Tu respuesta | Qué hice / haré |
|---|---|---|---|
| S-01 ✅ | Orden de construcción | Secuencial: 1, 2, 3, 4, 5… | La ruta Saga (01, 05, 06, 10, 12, 15, 16 y 17) ya estaba hecha. Desde ahí sigo en orden: 02 ✅, 03 ✅, 04 ✅, y los próximos son 07, 08, 09, 11, 13, 14 y 18 a 22. Hoy están listos del 01 al 21; falta el 22 (integrador) |
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
| S-15 ✅ | Ramas | Una rama por lab, con su merge | Desde el lab 04, cada cambio va en `lab/NN-nombre` (o `chore/…`, `docs/…`) y se integra a `main` con `git merge --no-ff`, así el historial muestra cada lab. Los labs anteriores ya estaban en `main` y no reescribí su historia |
| S-16 ✅ | Credenciales | En el repo privado `patrones-arquitectura-credentials` | Tiene los secretos de prod del lab 01, los de dev del lab 01 (`.env.dev.secrets`) y los de prod del lab 05 (`.env.prod.secrets`), con un `instalar.sh` que los copia a tu clon. La guía ignora `*.secrets` y `secrets/*.txt` |

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

- **L02-1 ✅ Puertos 8111 y 8112** para las versiones hexagonales (Quarkus y Python). La convención reserva 8101 para `catalog`, que aquí usa la versión en capas. **Respuesta:** OK.
- **L02-2 ✅ Las tres versiones comparten la base `catalog`**, cada una en su esquema (`layered`, `hexagonal`, `hexagonal_py`). **Respuesta:** OK, siempre que se pueda probar cada servicio. **Hecho:** ya es así: cada versión tiene su puerto y su esquema, y el smoke y los tests de API corren contra las tres.
- **L02-3 ✅ El mismo contrato HTTP en las tres** (JSON, códigos de error y estados), para que el smoke y los tests de API sean idénticos. **Respuesta:** OK.
- **L02-4 ✅ Clean y Onion no tienen versión propia**: se explican con una tabla de vocabulario sobre la versión hexagonal. **Respuesta:** OK, así se entiende mejor.
- **L02-5 ✅ El CLI solo existe en la versión Python.** **Respuesta:** queda como experimento para el lector.
- **L02-6 ✅ En memoria, la versión Quarkus sigue conectándose a Postgres al arrancar** (Flyway e Hibernate se inicializan siempre). **Respuesta:** queda como ejercicio.
- **L02-7 ✅ "Nombre único" se comprueba leyendo antes de escribir**, sin índice único. **Preguntaste cómo se soluciona y por qué sale ese signo.** El ⚠️ del README marca una simplificación consciente: con dos peticiones simultáneas, las dos leen "no existe" y las dos insertan. **Hecho:** el README del lab 02 tiene una sección nueva, "Cómo se arregla el nombre único":
  1. Un índice único en la base: `(inventor_id, lower(trim(name)))`. La base es la única que ve las dos escrituras.
  2. El adaptador de persistencia traduce el error de Postgres (`23505`) al mismo `409 NAME_ALREADY_USED`. El dominio no se entera de SQLState.
  3. La lectura previa queda como cortesía (un mensaje claro en el caso normal), pero ya no es la que garantiza la regla.
  4. Un test con dos inserciones simultáneas del mismo nombre: exactamente una falla.

### Lab 03 · DDD

- **L03-1 ✅ Un solo servicio con los dos modelos** (`/api/anemic/orders` y `/api/orders`). **Respuesta:** sí, es mejor: menos complejidad y menos recursos.
- **L03-2 ✅ El agregado lleva anotaciones de JPA.** **Respuesta:** no separarlas; queda como atajo pragmático (separarlas ya se enseña en el lab 02).
- **L03-3 ✅ La invariante "no se modifica después de pagado" quedó más estricta**: las líneas solo se editan en `DRAFT`. **Respuesta:** OK, buen ejemplo.
- **L03-4 ✅ `@Version` y el experimento de concurrencia** (el anémico pierde o duplica líneas; el agregado responde `409`). **Respuesta:** te agradó la idea.
- **L03-5 ✅ Los eventos de dominio son eventos CDI dentro del proceso**, con `AFTER_SUCCESS`. **Respuesta:** OK.
- **L03-6 ✅ El lado estratégico es documentación** (tabla de "Invento" por contexto y mapa de contextos). **Respuesta:** OK, la documentación es importante.

### Lab 04 · Monolito modular

- **L04-1 🟢 Puerto 8100 para `shop`**, que no estaba en la convención: el monolito junta catálogo, pedidos, inventario y pagos.
- **L04-2 🟢 Agregué un módulo `shipping`** que escucha `OrderPlaced`. Sirve para mostrar eventos dentro del proceso sin ciclos de dependencia y la consistencia eventual dentro de un monolito (`SHIPPING_CHAOS_FAIL`).
- **L04-3 🟢 La versión ingenua es un experimento**, no un servicio aparte: `experiments/romper-limites.sh` agrega una clase que viola un límite y muestra cómo falla el build. Corre con Docker, o con `LOCAL=1` si tienes Java 25.
- **L04-4 ✅ Un solo usuario de base de datos para todos los esquemas.** **Respuesta:** explicarlo con un esquema para todos. **Hecho:** el README del lab 04 tiene "Un usuario o un usuario por módulo": un diagrama de las dos opciones y una tabla con quién protege los límites, qué pasa con el SQL nativo, la transacción única, la configuración y cuándo conviene cada una (un usuario por módulo es el paso previo a separar un servicio).
- **L04-5 🟢 Base `shop`** (la misma que usaba la versión ingenua del lab 05), con un esquema por módulo creado por Flyway.

### Lab 05 · Microservicios

- **L05-1 ✅ Overlays de ambiente.** **Respuesta:** solo en los labs donde el patrón cambia entre ambientes. **Hecho:** se queda así: el lab 01 tiene los tres (`compose.override.yaml`, `compose.dev.yaml`, `compose.prod.yaml`), y los labs 05 y 06 tienen `compose.prod.yaml`. Los demás traen la sección "¿Qué cambia en dev y prod?" en su README. Qué es un overlay: [`docs/ambientes.md`](docs/ambientes.md).
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

### Lab 07 · API Gateway

- **L07-1 ✅ Spring Boot 4.0.9 + Spring Cloud 2025.1.4**, no 4.1. **Respuesta:** se hará en el futuro. Está en [`docs/actualizaciones-futuras.md`](docs/actualizaciones-futuras.md).
- **L07-2 ✅ Keycloak.** **Respuesta:** dejarlo en `infra/`, con un comentario de dónde viviría si solo lo usara el lab 07. **Hecho:** [`infra/keycloak.yaml`](infra/keycloak.yaml) con el realm en `infra/keycloak/mercado-realm.json`. El comentario dice que hoy lo usan el lab 07 y el 22, y que si solo lo usara el 07 podría vivir dentro de su `compose.yaml`. Smoke del lab 07 reverificado.
- **L07-3 🟢 Catálogo y pedidos guardan en memoria, sin Postgres.** El lab es sobre el borde (rutas, tokens, límites), no sobre persistencia. Así arranca más rápido y con menos RAM.
- **L07-4 🟢 El catálogo es público y no usa OIDC**; pedidos sí, y valida el token **otra vez** (defensa en profundidad). Así se ve que no todo detrás del gateway necesita autenticación.
- **L07-5 ✅ Tokens con el flujo *password*** (`token.sh`). **Respuesta:** está bien para este lab; cuando existan los otros labs, mostrar el flujo real. **Hecho:** el flujo real (Authorization Code + PKCE desde una web) va en el lab 22, que junta gateway, Keycloak y frontend. El lab 09 explica cómo sería con el BFF. Sobre "no veo L21": las decisiones del lab 21 se agregaron después de que leíste el archivo; están más abajo.
- **L07-6 🟢 CORS se configura en Spring Security** del gateway, y no en el `globalcors` de Spring Cloud Gateway: la seguridad corre antes que el gateway, así que el *preflight* necesita resolverse ahí.
- **L07-7 ✅ Con un servicio caído, el gateway responde `500`**, sin *fallback*. **Respuesta:** explicar dónde se profundiza. **Hecho:** el README del lab 07 apunta al lab 11 (timeout, retry, circuit breaker, bulkhead y fallback con una pasarela que falla a propósito) y nombra cómo se haría en el gateway: el filtro `CircuitBreaker` de Spring Cloud Gateway (Resilience4j) con una `fallbackUri`.
- **L07-8 ✅ Los servicios exponen sus puertos en local** solo para el experimento de "saltarse el gateway". **Respuesta:** OK.

### Lab 08 · Service Registry y Discovery

- **L08-1 🟢 Consul 2.1.0**, la última estable. La guía mencionaba Consul sin versión.
- **L08-2 🟢 Inventario en memoria y sin base**: el lab es sobre encontrar instancias. Cada respuesta dice qué réplica respondió (el ID del contenedor, que es su hostname), así se puede hacer `docker kill` de una réplica concreta.
- **L08-3 🟢 Los dos caminos conviven en el mismo pedidos** (`/api/discovery/dns/...` y `/api/discovery/consul/...`) para compararlos con las mismas réplicas.
- **L08-4 🟢 "Server-side discovery" se muestra con el DNS de Docker**, como decía el plan, y el README explica que en Kubernetes el `Service` agrega salud con los *readiness probes*. Un balanceador real (Traefik) quedó como ejercicio.
- **L08-5 🟢 Stork refresca cada 2 s** (el default es 5 minutos), para que los experimentos se vean en segundos.
- **L08-6 🟢 Eureka solo se compara en el README**, sin implementarlo: Stork lo soporta con un cambio de configuración.

### Lab 09 · BFF y API Composition

- **L09-1 🟢 Los tres servicios son en memoria y sin base**, con datos de ejemplo para `ana` y `beto`. El lab es sobre componer, no sobre persistir.
- **L09-2 🟢 Pagos y envíos aceptan varios pedidos por llamada** (`?orderIds=a,b,c`) para evitar el N+1. Es un cambio de API pensado para la composición, y el README lo explica.
- **L09-3 ✅ El cliente llega como parámetro** (`?customer=ana`), sin login. **Respuesta:** sin Keycloak (no agrega valor y hace el lab más pesado); dejar un comentario de cómo sería en prod. **Hecho:** el README del lab 09 tiene "Cómo sería el login en prod", con un diagrama: el BFF como *token handler* (Authorization Code + PKCE contra Keycloak, tokens guardados en el servidor, una cookie `HttpOnly` para el navegador, y el cliente sale del token). El mismo comentario está en el código, junto al endpoint del BFF.
- **L09-4 ✅ La web tiene un modo "Sin BFF"**, además de "en paralelo" y "en cascada". **Respuesta:** perfecto.
- **L09-5 🟢 La parte en Python usa `asyncio.gather` con `httpx`**, como decía el plan. El timeout es `asyncio.wait_for` por llamada, para que también funcione en los tests con `MockTransport`.
- **L09-6 🟢 Pedidos es imprescindible (`503` si falla); pagos y envíos son opcionales** (respuesta parcial). La guía decía "respuesta parcial" en general; separé los dos casos porque sin pedidos no hay pantalla.

### Lab 10 · Observabilidad

- **L10-1 ✅ La traza empieza en el gateway.** **Respuesta:** mostrar un caso real cuando exista el lab 07. **Hecho:** el lab 10 tiene ahora un gateway (Spring Cloud Gateway) delante de `orders`, y la traza va de punta a punta: gateway → orders → Kafka → inventory.
  - El gateway no tiene código de observabilidad: lo instrumenta el **agente de OpenTelemetry para Java** (`-javaagent`, versión 2.33.1), configurado solo con variables `OTEL_*`. Así la comparativa de instrumentación suma una tercera forma: Python a mano, Quarkus y Java con agente.
  - El smoke manda el pedido por el gateway y exige sus spans en la traza. Captura nueva en `docs/traza.png`.
  - Experimento nuevo: apagar el agente (`OTEL_JAVAAGENT_ENABLED=false`) y ver que el gateway desaparece de la traza.
  - El navegador no aparece en la traza: instrumentarlo (RUM, por ejemplo Grafana Faro) es otro tema. La traza empieza en la puerta de entrada del backend, como en la mayoría de los sistemas.
- **L10-2 ✅ Pedidos consulta el stock por HTTP antes de publicar.** Lo vas a revisar.
- **L10-3 ✅ En Python la telemetría se configura a mano.** **Respuesta:** hacer un ejemplo con Quarkus (o Spring si no lo soporta) y una comparativa en un `.md`. **Hecho:** Quarkus lo soporta. Pedidos ahora tiene:
  - un span con `@WithSpan` (`orders.check-stock`, en `StockCheck`);
  - un span creado con el `Tracer` (`orders.save-and-publish`, con `Scope`, evento y manejo de errores);
  - una métrica de negocio con el `Meter` (`orders_placed_total{result}`).

  Hay tests con un exportador en memoria, chequeos en `smoke.sh` y una captura nueva. La comparativa está en [`docs/instrumentacion-manual-vs-automatica.md`](labs/10-observabilidad/docs/instrumentacion-manual-vs-automatica.md): Python a mano, Python con `opentelemetry-instrument`, Quarkus, y una tabla de Spring Boot. También bajé la exportación de métricas de Quarkus de 60 s a 10 s.
- **L10-4 ✅ SQLAlchemy 2.0.57 en este lab.** **Respuesta:** dejarlo como actualización futura. Ya está en [`docs/actualizaciones-futuras.md`](docs/actualizaciones-futuras.md).

### Lab 11 · Resiliencia

- **L11-1 🟢 La pasarela simulada (`payment-provider`, puerto 8122) es FastAPI**, con controles de caos (latencia, tasa de errores, caída total), idempotencia por clave y un contador de peticiones recibidas para medir el *retry storm*.
- **L11-2 🟢 Tres caminos en pagos:** `/naive` (sin protección), `/naive-retry` (reintentos inmediatos) y el resiliente. Así se comparan con la misma pasarela.
- **L11-3 🟢 El fallback deja el pago `PENDING_CONFIRMATION`** (`202`) y un reconciliador (`@Scheduled` cada 5 s) lo confirma después, con la misma `idempotencyKey`. El plan decía "para reintentarlo después"; el reconciliador es ese "después".
- **L11-4 🟢 El circuit breaker ignora los rechazos de negocio (`402`) y los del bulkhead.** Lo segundo apareció con la carga de k6: con la JVM fría, el bulkhead rechazaba llamadas, eso contaba como falla y el circuito se abría sin que la pasarela fallara. Está contado en el README.
- **L11-5 ✅ En Python, solo Retry con tenacity.** **Respuesta:** está bien; las alternativas (`pybreaker`, `asyncio.Semaphore`) quedan nombradas para saber que existen.
- **L11-6 🟢 Las métricas son propias** (`payments_circuit_state`, `payments_outcomes_total`, `payments_provider_calls_total`) con el `Meter` de OpenTelemetry, en lugar de las métricas internas de SmallRye Fault Tolerance, porque así se leen directo en Prometheus. Sin un dashboard provisionado: el README da las consultas para Explore, y el gráfico del README sale de esos mismos datos.
- **L11-7 🟢 k6 2.4.0 en un perfil `load`**, sin `latest`. El experimento de la caída de la pasarela se hace a mano (el README da los comandos).

### Lab 12 · Idempotencia

- **L12-1 🟢 No hay servicio de pedidos:** los eventos se publican con `publish-event.sh`. Así el lector controla exactamente cuándo se repite un evento.
- **L12-2 🟢 Concurrencia resuelta con la `PRIMARY KEY` + `INSERT … ON CONFLICT DO NOTHING`**, sin locks explícitos ni Redis. Es lo más simple que funciona con Postgres.
- **L12-3 🟢 Caos "muere antes del commit"** con un archivo marcador, para que se caiga **una sola** vez y no entre en un bucle de reinicios.

### Lab 13 · Cache-Aside

- **L13-1 🟢 La consulta "cara" se simula con `pg_sleep`** (200 ms, `DB_DELAY_MS`). Sin eso, Postgres responde en 1 ms y la caché no muestra su valor.
- **L13-2 ✅ Dos servicios `catalog-1` y `catalog-2`** en lugar de `--scale`. **Respuesta:** OK: escalar no es el propósito del lab, así que puertos fijos.
- **L13-3 ✅ Las cachés están escritas a mano.** **Preguntaste por qué no `@CacheResult`.** Para que se vea cada paso (buscar, si no está leer la base, guardar con TTL, invalidar), que con las anotaciones queda oculto. Pero vale la pena verlo también con anotaciones. **Hecho:** cuarto modo `?cache=annotated` ([`AnnotatedCache.java`](labs/13-cache-aside/services/catalog/src/main/java/com/mercadoinventos/catalog/AnnotatedCache.java)): la misma caché local con `@CacheResult` y `@CacheInvalidate`, con tests y en el smoke. Lo que se midió:
  - Con k6: 0,8 ms de p95 y **4** consultas a la base, contra 13 de la caché local escrita a mano. `@CacheResult` trae su propio candado por clave.
  - Ese candado es **de cada instancia**: 20 lecturas repartidas entre `catalog-1` y `catalog-2` hicieron 2 consultas (una por instancia), contra 1 con el candado en Redis.
  - Cachea también lo que no existe (`Optional.empty()`), sin pedirlo.

  El README tiene la tabla comparativa y cuándo conviene cada una.
- **L13-4 ✅ El stampede se mitiga con un candado en Redis** (`SET NX PX`). **Pediste más detalle y dónde profundizar.** **Hecho:** el README del lab 13 tiene "El candado, paso a paso": un diagrama con dos lecturas que llegan a la vez, qué hace `NX` (solo una lo consigue), qué hace `PX 2000` (vence solo si la instancia muere), cómo esperan las demás y por qué está en Redis y no en memoria. También un ⚠️: el `DEL` borra el candado sea de quien sea, y la versión robusta usa un valor único y un script Lua. Para profundizar en el proyecto: `InventionReader.readThroughRedis`, `RedisCache.tryLock`, el test `stampedeProtectionLetsOnlyOneRequestRebuildAnExpiredKey` y el experimento 4.
- **L13-5 🟢 La protección contra el stampede se cambia en caliente** (`PUT /admin/stampede-protection`), sin reiniciar, para comparar con y sin.
- **L13-6 ✅ Sin caché negativa.** **Preguntaste por el círculo amarillo.** Es 🟡 porque es un riesgo real que dejé a propósito: el lab no cachea "lo que no existe", así que alguien que pida 1000 SKUs inexistentes provoca 1000 consultas caras a la base. En prod se cachea también el "no existe", con un TTL corto. Lo dejé como ejercicio, y el ⚠️ del README avisa. Novedad: el modo `@CacheResult` (L13-3) sí lo cachea, sin pedirlo.

### Lab 14 · CQRS

- **L14-1 🟢 Pagos y envíos están simulados** por un solo proceso (`fulfillment`) que, por cada `OrderPlaced`, publica `PaymentConfirmed` y `ShipmentDispatched`. El lab trata de cómo la vista **junta** eventos de varios servicios, no de cómo se paga (eso es de los labs 16 y 17).
- **L14-2 🟢 Demora artificial de 1,5 s por evento** en el proyector (`PROJECTION_DELAY_MS`), para que la consistencia eventual se vea en la web. Sin ella, la vista se actualiza en milisegundos y las tres técnicas parecen iguales.
- **L14-3 🟢 El proyector y la API de lectura son la misma imagen con dos comandos**, en procesos separados: si el proyector cae, la API sigue respondiendo.
- **L14-4 🟢 Reconstruir = cambiar `PROJECTION_VERSION`.** El proyector vacía la colección y vuelve al offset 0 (con un `ConsumerRebalanceListener`). Primero lo hice con un consumer group por versión, pero al **volver** a una versión anterior, ese grupo ya tenía offsets guardados y la vista quedaba vacía para siempre. El *blue/green* (otra colección) solo se explica.
- **L14-5 ✅ Los consumidores Python manejan `SIGTERM`.** **Preguntaste cómo se podría solucionar.** Ya está solucionado en el lab: 3 líneas con `add_signal_handler(SIGTERM, …)` hacen que el proceso cierre el consumidor y salga del consumer group al instante. **Hecho:** el README del lab 14 ahora compara las alternativas: `init: true` (o `tini`) solo evita la espera de 10 s, pero Python muere sin correr el `finally` y no sale del grupo igual; alargar `stop_grace_period` o bajar `session.timeout.ms` tampoco lo resuelven. El handler es el arreglo de fondo.
- **L14-6 ✅ `orders` guarda y después publica (escritura doble).** **Preguntaste por el círculo amarillo.** Es 🟡 porque es un defecto real aceptado a propósito: si `orders` muere entre guardar y publicar, el pedido existe pero el historial **nunca** lo muestra, y reconstruir la vista no ayuda porque el evento no está en Kafka. **Hecho:** el README del lab 14 explica la solución en 3 pasos: guardar el evento en una tabla `outbox` en la misma transacción que el pedido, publicarlo con un proceso aparte (polling o Debezium, lab 15), y la proyección ya es idempotente para tolerar los repetidos.
- **L14-7 🟢 El historial guarda el nombre del invento del momento de la compra.** Si el catálogo cambia el nombre, el historial no se actualiza: para un historial es lo correcto, y el README lo explica en "Datos duplicados".

### Lab 15 · Transactional Outbox

- **L15-1 🟢 Un solo servicio con tres modos** (`OUTBOX_MODE=naive|polling|cdc`) para comparar dual write, polling y CDC con el mismo código de negocio.
- **L15-2 ✅ Debezium se conecta con el superusuario `admin`.** **Preguntaste por qué el signo y si un usuario dedicado agregaría valor.** Es 🟡 porque un superusuario puede leer y cambiar todo: si se filtra la clave del conector, se filtra la base. En prod, un usuario dedicado agrega mucho valor (mínimo privilegio). Para aprender el patrón, poco. **Decisión (tu preferencia):** sin usuario dedicado, para no complicar `infra/postgres/init`, que comparten todos los labs. **Hecho:** el README del lab 15 muestra el SQL del usuario de prod (`REPLICATION`, `SELECT` solo sobre la outbox, publicación creada por el administrador) y qué cambiar en el conector.
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

### Lab 18 · Event Sourcing

- **L18-1 🟢 El event store es una tabla de Postgres escrita a mano**, con JDBC directo (sin Hibernate ni librerías de Event Sourcing), para que se vea cada SQL. El README nombra KurrentDB, Axon y Marten como alternativas.
- **L18-2 🟢 Los datos de ejemplo traen depósitos en formato v1** (`amountCents`), escritos por la migración. Así el upcaster trabaja con eventos reales desde el primer `docker compose up`, y el ejercicio es escribir otro (v1 → v2 de `FundsWithdrawn`).
- **L18-3 🟢 El proyector filtra por id de transacción (`xid8` + `pg_snapshot_xmin`)**, no solo por posición. Con la posición sola, un evento que confirma tarde se pierde. Hay un test que lo reproduce, y comprobé que falla con la consulta ingenua.
- **L18-4 🟢 El proyector corre dentro del servicio, cada 1 s** (el scheduler de Quarkus no acepta menos). Con varias réplicas habría que elegir una sola activa; el README lo explica.
- **L18-5 🟢 `PUT /admin/think-time` abre una ventana entre leer y guardar** para que la concurrencia se vea siempre igual (en el smoke y en los tests). Sin ella, dos peticiones de curl casi nunca se cruzan.
- **L18-6 🟢 Sin frontend**: el catálogo decía solo Quarkus. La historia se ve con `GET /events`.
- **L18-7 🟢 Jackson lee los decimales como `BigDecimal`** (`USE_BIG_DECIMAL_FOR_FLOATS` y nodos exactos). Sin eso, 500.00 se convierte en 5E+2 o en un `double`.

### Lab 19 · Anti-Corruption Layer

- **L19-1 🟢 El legado es un simulador FastAPI** ("Rápido SAC"), con las rarezas típicas: XML en ISO-8859-1, códigos crípticos, libras con coma decimal, fechas `dd/MM/yy`, UBIGEO y errores con HTTP 200. Trae una API de administración para avanzar estados y cambiar de versión, que el real no tendría.
- **L19-2 🟢 El "legado cambia un código" es la versión 2 del simulador** (`PUT /admin/version`): `EST=3` pasa a `7` y aparece `8`. El arreglo (2 filas en el traductor) **no** viene hecho: es el paso 4 del "Rómpelo". Lo probé antes de escribirlo en el README.
- **L19-3 🟢 Un código desconocido se convierte en `UNKNOWN` + `WARNING`**, en lugar de un error 500 o de dejar pasar el código. Es una decisión de diseño explicada en el README.
- **L19-4 🟢 Tests de contrato en dos niveles:** respuestas guardadas del legado (`tests/fixtures`, en ISO-8859-1) y un `contract_check` ejecutable contra el legado vivo. No usé Pact: para un legado que no va a correr tests de proveedor, alcanza con esto.
- **L19-5 🟢 La versión ingenua vive en el mismo servicio** (`/api/naive/shipments`), y el frontend que la consume solo aparece como un fragmento de TypeScript en el README. El catálogo decía solo FastAPI.
- **L19-6 🟢 Los envíos se guardan en memoria**, sin Postgres. El lab trata de la integración.
- **L19-7 🟢 `defusedxml` para leer XML** (también en el simulador). `xml.etree` no se protege de entidades maliciosas, y un XML que viene de afuera es justamente lo que un ACL recibe.

### Lab 20 · Strangler Fig

- **L20-1 🟢 Las rutas del gateway están en Java (`Routes.java`) y no en YAML**: sus predicados leen el estado de la migración en cada petición, y ese estado se cambia con `PUT /admin/migration`, sin reiniciar. El README nombra el predicado `Weight` de Spring Cloud Gateway como alternativa por configuración.
- **L20-2 🟢 El canary sortea cada petición**, no cada cliente. Así se ve el problema de la ventana de sincronización (experimento 4), y el canary pegajoso queda como ejercicio.
- **L20-3 🟢 El catálogo se migra con una copia inicial** porque en el lab es de solo lectura. Los pedidos se sincronizan en los dos sentidos cada segundo, con rangos de ids separados (1001… y 500001…) y copias idempotentes.
- **L20-4 🟢 Al legado se le agregaron `/internal/orders` y la columna `origen`.** Sin tocarlo, la única forma de sincronizar sería leer su base o usar CDC (lab 15), y el lab sumaría Debezium.
- **L20-5 🟡 El estado de la migración vive en la memoria del gateway**, sin autenticación (⚠️ en el README). Con varias réplicas del gateway no serviría.
  **Pregunta:** ¿quieres que el estado salga de Consul KV o de un archivo de configuración con refresco, para mostrar cómo se haría con varias réplicas?
- **L20-6 🟢 Sin frontend**: el catálogo nombraba gateway, Quarkus y FastAPI. Lo que se migra se ve con las cabeceras `X-Route` y `X-Served-By`.

### Lab 21 · Micro-frontends

- **L21-1 🟢 `@module-federation/vite` 1.24.1**, el plugin oficial de Module Federation para Vite (compatible con Vite 8). El de OriginJS quedó atrás.
- **L21-2 🟢 Un paquete local `@mercado/platform`** con el design system y el contrato de eventos, compartido como singleton. En una empresa sería un paquete versionado en un registro privado; aquí entra a cada imagen con `additional_contexts`, como `shared/`.
- **L21-3 🟢 El shell sirve los remotos por su nginx** (`/remotes/<equipo>/`): mismo origen, sin CORS. En prod lo haría un CDN o un ingress.
- **L21-4 🟢 Sin backend:** los productos, el carrito y los pedidos viven en el navegador. El lab trata de la composición de la interfaz; el README dice que cada remoto usaría el BFF de su equipo (lab 09).
- **L21-5 🟢 La "versión nueva" de pedidos es un argumento de build** (`ORDERS_VERSION=2` agrega "Repetir pedido"). Así el despliegue independiente y el rollback se prueban sin editar código.
- **L21-6 🟢 El smoke prueba por HTTP** (rutas, caché, remotos caídos). Lo del navegador (React una sola vez, eventos, fallback, despliegue de la v2, el experimento sin singleton) lo verifiqué con Playwright y quedó en capturas. No agregué Playwright como dependencia del lab.
