# infra/ — bloques de infraestructura compartidos

Cada archivo define uno o más servicios de infraestructura listos para importar desde un lab con `include:`:

```yaml
# labs/NN-algo/compose.yaml
name: labNN-algo
include:
  - ../../infra/postgres.yaml
  - ../../infra/kafka.yaml
services:
  orders:
    build: ./services/orders
    depends_on:
      postgres:
        condition: service_healthy
```

| Archivo | Servicios | Puertos en tu máquina | Usado en |
|---|---|---|---|
| `postgres.yaml` | `postgres` | 5432 | Casi todos |
| `kafka.yaml` | `kafka`, `kafka-ui` | 9092, 8090 | 06 en adelante |
| `redis.yaml` | `redis` | 6379 | 07, 13 |
| `observability.yaml` | `lgtm` (Grafana, Tempo, Loki, métricas) | 3001, 4317, 4318 | 10 en adelante |
| `optional/kafka-zookeeper.yaml` | `zookeeper`, `kafka` (3.9), `kafka-ui` | 9092, 8090 | Opcional: reemplaza a `kafka.yaml` para ver el modo ZooKeeper ([explicación](../docs/kafka-kraft-vs-zookeeper.md)) |

Los bloques que solo usa un lab se definen **dentro de ese lab**. Por ejemplo, Debezium vive en [`labs/15-transactional-outbox/compose.yaml`](../labs/15-transactional-outbox/compose.yaml). Si más adelante otro lab los necesita, se mueven aquí.

## Cómo funciona `include`

- Las rutas **dentro** de un archivo de `infra/` (por ejemplo `./postgres/init`) se resuelven respecto de `infra/`, no del lab.
- Las variables (`${POSTGRES_PORT:-5432}`) se toman del `.env` **del lab**, así que cada lab puede cambiar puertos o credenciales sin tocar `infra/`.
- Los overlays de ambiente del lab (`compose.prod.yaml`) **sí** pueden modificar servicios incluidos. Por ejemplo, para no exponer Postgres en prod:

  ```yaml
  services:
    postgres:
      ports: !reset []
  ```

  `!reset` requiere Docker Compose **2.24 o superior**.

## Postgres: una base por servicio

En el primer arranque, `postgres/init/01-databases.sh` crea una base y un usuario para cada nombre de `APP_DATABASES`: `catalog`, `orders`, `inventory`, `payments`, `shipping`, `wallet`, `order_saga`, `order_history`, `legacy_shop` y `shop` (la base compartida de la versión ingenua del lab 05).

- El usuario `orders` con contraseña `orders` solo puede entrar a la base `orders`. Si intenta conectarse a `inventory`, recibe `User does not have CONNECT privilege`.
- El script **solo corre con el volumen vacío**. Si cambias la lista, ejecuta `docker compose down -v`.
- El healthcheck usa `pg_isready -h 127.0.0.1` (TCP), no el socket Unix. Mientras corren los scripts de init, la imagen levanta un Postgres **temporal que solo escucha en el socket**. Con un healthcheck por socket, el contenedor queda *healthy* antes de tiempo, y un servicio que arranca en esa ventana falla al conectarse por TCP. Pasó al reverificar el lab 10, y se reprodujo consultando los dos modos en paralelo durante el arranque. Lección: un healthcheck debe comprobar el servicio **como lo usan sus clientes**.

## Kafka: dos listeners

| Desde | Dirección |
|---|---|
| Un contenedor del lab | `kafka:9092` |
| Un proceso en tu máquina (modo desarrollo) | `localhost:9092` |

Kafka les dice a los clientes a qué dirección reconectarse (*advertised listeners*). Por eso se necesitan dos: el nombre `kafka` no existe fuera de Docker, y `localhost` dentro de un contenedor apunta al propio contenedor.
