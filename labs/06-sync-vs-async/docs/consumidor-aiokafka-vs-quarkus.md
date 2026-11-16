# El mismo consumidor en aiokafka y en Quarkus

El inventario del lab 06 consume `orders.events` en **dos implementaciones** que hacen exactamente lo mismo:

| | Python | Java |
|---|---|---|
| Servicio | [`services/inventory`](../services/inventory) | [`services/inventory-quarkus`](../services/inventory-quarkus) (perfil `quarkus`) |
| Librería | [aiokafka](https://aiokafka.readthedocs.io/) | [Quarkus Messaging](https://quarkus.io/guides/kafka) (SmallRye Reactive Messaging) |
| Grupo de consumidores | `inventory` | `inventory-quarkus` |
| DLQ | `orders.events.dlq` | `orders.events.dlq-quarkus` |
| Puerto | 8103 | 8133 |

Como están en **grupos distintos**, Kafka les entrega **a los dos** cada evento: es Publish/Subscribe. Cada uno guarda sus reservas en su propio esquema de la base `inventory`.

```bash
docker compose --profile quarkus up --build -d
./smoke-quarkus.sh
```

## Las reglas son las mismas

Para cada mensaje:

| Caso | Qué debe pasar |
|---|---|
| Se procesa bien | Se confirma el offset **después** de procesar, y se sigue con el siguiente |
| Mal formado (veneno) | Va a la DLQ y se sigue. Reintentarlo no sirve: fallaría siempre y bloquearía la partición |
| Falla transitoria (pedidos caído, en eventos *notification*) | Se reintenta con espera hasta que funcione. Mientras tanto, los mensajes siguientes esperan |

## Lado a lado

### 1. Suscribirse y confirmar el offset

**aiokafka** ([`consumer.py`](../services/inventory/app/consumer.py)): todo es explícito en el código.

```python
consumer = AIOKafkaConsumer(
    settings.kafka_topic,
    group_id=settings.kafka_group_id,
    enable_auto_commit=False,          # confirmamos a mano, DESPUÉS de procesar
    auto_offset_reset="earliest",
)
async for message in consumer:
    await process_message(message.value, handler, dlq, settings.retry_backoff_seconds)
    await consumer.commit()
```

**Quarkus** ([`application.properties`](../services/inventory-quarkus/src/main/resources/application.properties) y [`OrderEventsConsumer.java`](../services/inventory-quarkus/src/main/java/com/mercadoinventos/inventory/OrderEventsConsumer.java)): el bucle no existe; lo pone el framework.

```properties
mp.messaging.incoming.orders-events.topic=orders.events
mp.messaging.incoming.orders-events.group.id=inventory-quarkus
mp.messaging.incoming.orders-events.enable.auto.commit=false
mp.messaging.incoming.orders-events.commit-strategy=throttled
```

```java
@Incoming("orders-events")
@Blocking
public void consume(String raw) { ... }   // si termina sin excepción → ack → offset confirmado
```

`throttled` confirma periódicamente el **mayor offset cuyos mensajes anteriores ya fueron procesados**. Nunca confirma un mensaje que todavía no terminó.

### 2. Mensaje veneno → DLQ

**aiokafka:** se programa a mano. Hay que capturar la excepción, tener un productor y publicar en la DLQ.

```python
except InvalidEvent as error:
    await dlq.send(raw, str(error))        # KafkaDlq: un AIOKafkaProducer + header dlq-reason
    return "DLQ"
```

**Quarkus:** es **configuración**. El método lanza `InvalidEventException`, el mensaje queda como *nack* y la estrategia de fallo lo publica en la DLQ.

```properties
mp.messaging.incoming.orders-events.failure-strategy=dead-letter-queue
mp.messaging.incoming.orders-events.dead-letter-queue.topic=orders.events.dlq-quarkus
```

SmallRye agrega por su cuenta los headers `dead-letter-reason` (el mensaje de la excepción), `dead-letter-exception-class-name`, `dead-letter-topic`, `dead-letter-partition` y `dead-letter-offset` (y `dead-letter-cause` si la excepción tiene una causa). Con aiokafka, esos datos los agregas tú, o no los tienes. Para verlos:

```bash
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic orders.events.dlq-quarkus --from-beginning --formatter-property print.headers=true
```

### 3. Fallas transitorias → reintentos

**aiokafka:** un `while True` con `asyncio.sleep` y un log por intento.

```python
except TransientError as error:
    log.warning("Falla transitoria (intento %d), reintento en %.1fs: %s", attempt, backoff_seconds, error)
    await asyncio.sleep(backoff_seconds)
```

**Quarkus:** SmallRye Fault Tolerance, con anotaciones.

```java
@Retry(maxRetries = -1, delay = 2, delayUnit = ChronoUnit.SECONDS,
       maxDuration = 0, abortOn = InvalidEventException.class)
@BeforeRetry(LogRetryHandler.class)
```

Tres detalles que se aprendieron al construirlo:

- **`maxDuration = 0`.** Por defecto, `@Retry` deja de reintentar a los **3 minutos**, aunque `maxRetries = -1`. Si pedidos se cae 5 minutos, sin `maxDuration = 0` el evento terminaría en la DLQ.
- **`abortOn`.** Sin él, `@Retry` también reintentaría los mensajes veneno, para siempre.
- **`@Retry` es silencioso.** No deja ningún log entre intentos. [`LogRetryHandler`](../services/inventory-quarkus/src/main/java/com/mercadoinventos/inventory/LogRetryHandler.java) los registra como hace la versión Python. La variante `@BeforeRetry(methodName = ...)` falló en esta versión de SmallRye (invoca el método con argumentos, aunque exige que no tenga), así que se usa la variante con una clase.

### 4. Qué pasa si el reintento dura mucho

Las dos versiones procesan **en orden**: mientras un mensaje se reintenta, los siguientes de esa partición esperan. Pero reaccionan distinto si la espera es larga:

- **aiokafka:** si un mensaje tarda más que `max_poll_interval_ms` (5 minutos por defecto), Kafka considera muerto al consumidor y **reparte sus particiones** a otro miembro del grupo, que vuelve a procesar el mismo mensaje.
- **Quarkus:** SmallRye **pausa** las particiones mientras hay mensajes pendientes y sigue haciendo `poll`. El consumidor no sale del grupo, aunque el reintento dure horas.

### 5. Alternativa que no bloquea la partición

Las dos versiones bloquean la partición mientras reintentan: es lo correcto si el **orden** importa (los eventos de un pedido deben procesarse en orden). Si no importa, Quarkus ofrece `failure-strategy=delayed-retry-topic`: el mensaje fallido se mueve a topics de reintento con demoras crecientes y la partición sigue avanzando. En aiokafka habría que construirlo a mano.

## Resumen

| Aspecto | aiokafka | Quarkus Messaging |
|---|---|---|
| Bucle de consumo | Lo escribes tú (`async for`) | Lo pone el framework (`@Incoming`) |
| Confirmar el offset | `consumer.commit()` explícito | `ack` automático al terminar el método (`throttled`) |
| DLQ | Productor propio y código | `failure-strategy=dead-letter-queue` |
| Headers de la DLQ | Los que agregues | Motivo, clase de la excepción, topic, partición y offset de origen |
| Reintentos | Bucle con `sleep` | `@Retry` (ojo con `maxDuration`) |
| Reintento largo | Riesgo de rebalanceo tras `max_poll_interval_ms` | Pausa las particiones; no sale del grupo |
| Retry sin bloquear | A mano | `delayed-retry-topic` |
| Salud | Lo que programes en `/health` | `/q/health/ready` incluye el estado del canal (`"orders-events": "[OK]"`) |
| Tests | Funciones puras + fakes | Kafka real con Dev Services, o conector en memoria |
| Líneas del consumidor | ~80 (`consumer.py`) | ~50 (`OrderEventsConsumer.java`) + 8 líneas de configuración |

**Cuándo cada uno:**

- **aiokafka** cuando quieres **ver y controlar** cada paso, o el servicio es Python. Es la mejor forma de **aprender** cómo funciona un consumidor; por eso es la versión principal del lab.
- **Quarkus Messaging** cuando quieres que las decisiones de confiabilidad (commit, DLQ, reintentos) sean **configuración estándar** y no código que cada equipo reescribe y prueba por su cuenta.

## ¿Y en Spring?

Quarkus soporta todo lo de este lab, así que no fue necesario usar Spring. Como referencia, el equivalente en **Spring Kafka** es:

| Necesidad | Quarkus | Spring Kafka |
|---|---|---|
| Consumir | `@Incoming("canal")` | `@KafkaListener(topics = "...")` |
| Commit después de procesar | `commit-strategy=throttled` | `AckMode.RECORD` o `BATCH` (por defecto) |
| DLQ | `failure-strategy=dead-letter-queue` | `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` |
| Reintentos bloqueantes | `@Retry` | `DefaultErrorHandler` con `BackOff` (`FixedBackOff`, `ExponentialBackOff`) |
| No reintentar ciertos errores | `abortOn` | `addNotRetryableExceptions(...)` |
| Reintentos sin bloquear | `delayed-retry-topic` | `@RetryableTopic` |
