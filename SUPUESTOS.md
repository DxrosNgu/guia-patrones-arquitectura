# Supuestos y preguntas abiertas

Este archivo reúne las decisiones que tomé **sin poder consultarte**, mientras construía la guía. Cada una explica qué decidí, por qué y qué te pregunto. Responde lo que quieras cambiar (por ejemplo: "S-06: sí, TypeScript") y lo ajusto.

**Cómo leerlo:**
- 🟢 Decisión de bajo riesgo: cambiarla es barato.
- 🟡 Conviene que la revises: cambiarla toca varios labs.
- 🔴 Necesito tu respuesta antes de seguir con algo concreto.

---

## Alcance y orden

### S-01 🟡 Orden de construcción: primero la ruta "Saga a fondo"
**Decidí:** construir primero la base del repo y los labs **01 → 05 → 06 → 10 → 12 → 15 → 16 → 17**. Después sigo con el resto en orden numérico.
**Por qué:** tu interés de origen era Saga, y esos labs son exactamente los prerrequisitos de una saga confiable (microservicios, Kafka, observabilidad, idempotencia y outbox). Si la noche no alcanza para los 22 labs, al menos queda completa la ruta que más te importa.
**Pregunta:** ¿te sirve ese orden o prefieres el numérico?

### S-02 🟡 Estructura del repo: labs independientes con infraestructura compartida
**Decidí:** la opción 1 que te propuse (no llegaste a elegir). Cada lab tiene sus servicios y su `compose.yaml`, y la infraestructura común vive en `infra/` y se importa con `include:`.
**Costo:** hay código repetido entre labs (por ejemplo, `orders` aparece en varios).
**Pregunta:** ¿confirmas la opción 1?

### S-03 🟢 El upgrade de tus servicios a Spring Boot 4 no está incluido
**Decidí:** no tocar `api-gateway`, `auth-service`, `config-server`, `eureka-server` ni `inventions-payments`. Acordamos que es un proyecto aparte.
**Pregunta:** ¿lo planificamos cuando revises la guía?

---

## Stack y versiones

### S-04 🟢 Java 25 + Quarkus 3.40.1
**Decidí:** Java 25 (el LTS más reciente) y Quarkus **3.40.1**, la última versión estable. Quarkus 4.0 está en *CR1* (todavía no es final).
**Pregunta:** ¿migramos a Quarkus 4 cuando salga la versión final?

### S-05 🟢 Spring Boot 4.0.x + Spring Cloud 2025.1.x para el API Gateway
**Decidí:** en los labs que usan Spring (07 y 20), usar Spring Boot 4.0.x con Spring Cloud 2025.1.x. Spring Boot 4.1 ya existe, pero su release train de Spring Cloud (2026.0) sigue en RC.
**Pregunta:** ninguna por ahora. Lo actualizo cuando 2026.0 sea final.

### S-06 🟡 React en JavaScript, no en TypeScript
**Decidí:** JavaScript (JSX), igual que tu portafolio. Así el foco queda en la arquitectura y no en los tipos.
**Pregunta:** ¿prefieres TypeScript? Cambiarlo toca todos los frontends.

### S-07 🟢 Python con `pip` + `requirements.txt`
**Decidí:** `pip` con versiones fijadas en `requirements.txt` en lugar de `uv` o `poetry`, porque cualquier developer lo entiende sin instalar nada.
**Pregunta:** ¿prefieres `uv` con `pyproject.toml`?

### S-08 🟢 Código en inglés, documentación en español
**Decidí:** nombres de clases, eventos y endpoints en inglés (`OrderCreated`, `/orders`). Los README y los comentarios didácticos están en español.

### S-09 🟢 Kafka como broker
**Decidí:** Apache Kafka 4.1 en modo KRaft (sin ZooKeeper). Permite releer eventos (CQRS, Event Sourcing) y se integra con Debezium. RabbitMQ queda mencionado como alternativa.

### S-10 🟢 Imágenes Docker
**Decidí:** usar Docker Hub siempre que se pueda. Las excepciones:
- Debezium 3.x solo se publica en `quay.io`.
- Para Kafka UI uso `kafbat/kafka-ui`, el fork mantenido del proyecto de Provectus.

Todas las imágenes tienen la versión fijada (nunca `latest`).

---

## Ambientes y seguridad

### S-11 🟡 `.env.dev` y `.env.prod` se versionan
**Decidí:**
- `.env.dev` y `.env.prod` **sí** se suben a Git, porque solo tienen configuración no secreta (nivel de log, flags, URLs).
- Los secretos de prod van en `secrets/*.txt`. Se crean copiando los `*.example` y **no** se suben.
- `.env` (local) se crea desde `.env.example` y tampoco se sube.

**Por qué:** así los tres ambientes se pueden levantar justo después de clonar, y queda claro qué es configuración y qué es secreto.
**Pregunta:** ¿te parece bien esa separación?

### S-12 🟢 Un solo Postgres con una base y un usuario por servicio
**Decidí:** en local, un único contenedor Postgres que crea una base y un usuario por servicio, donde la contraseña es igual al nombre (`orders`/`orders`). El README del lab 05 explica que en prod serían instancias separadas.

---

## Repositorio

### S-13 🔴 Licencia
**Decidí:** no agregué licencia. Un repo público **sin licencia** significa "todos los derechos reservados", y otros developers no pueden reutilizar el código legalmente.
**Pregunta:** ¿agrego MIT (o la que prefieras)?

### S-14 🟡 La sección 8 del README menciona tus repos de `microservices-team`
**Decidí:** mantenerla, porque conecta la guía con tu trabajo real.
**Pregunta:** el repo de la guía es **público**. ¿Los repos de `microservices-team` también lo son? Si son privados, quizás prefieras quitar esa sección o generalizarla.

---

## Decisiones por laboratorio

*(Las voy agregando a medida que construyo cada lab.)*

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
- **L16-2 🟢 Kit compartido para los dos participantes Python** (`services/py-saga-kit`), con outbox, inbox, envelope y consumidor. Para poder copiarlo, el contexto de build de esos servicios es `services/`. En Java, las 4 clases equivalentes están duplicadas en `orders` y `payments`.
- **L16-3 🟢 Cancelar solo se permite antes del pivote (el cobro).** Después responde `409`. Las carreras entre la cancelación y el cobro se resuelven recordando las cancelaciones en pagos, inventario y envíos.
- **L16-4 🟢 S6 (timeout del pago) y S13 (participante que nunca responde) quedan para el lab 17:** necesitan un coordinador que mida el tiempo de la saga.
- **L16-5 🟢 Agregué `INV-005` con 1 unidad** al stock inicial, para el caso S12.

### Lab 17 · Saga por orquestación

- **L17-1 🟢 El orquestador vive dentro de `orders`**, como en el libro de Richardson, y no en un servicio `order-saga` aparte (como decía el plan). Es un servicio menos y el flujo sigue en un solo archivo.
- **L17-2 🟢 Timeouts:** 5 s por paso y 3 intentos antes del pivote. Después del pivote y en las compensaciones se reintenta para siempre. Todo es configurable (`SAGA_STEP_TIMEOUT`, `SAGA_MAX_ATTEMPTS`).
- **L17-3 🟡 Temporal no está implementado:** solo aparece en el README como alternativa y ejercicio avanzado.
  **Pregunta:** ¿quieres una variante con Temporal?
- **L17-4 🟢 Este es el primer frontend en TypeScript** (tu respuesta S-06). Los de los labs 01 y 16 los migro después.
