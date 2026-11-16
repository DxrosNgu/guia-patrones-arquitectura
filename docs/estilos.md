# Estilos de arquitectura

Un **estilo** de arquitectura es la forma general del sistema: cómo se divide y cómo se comunican sus partes. Un **patrón** resuelve un problema concreto dentro de un estilo. Por ejemplo, "microservicios" es un estilo y "Saga" es un patrón que aparece cuando eliges ese estilo.

Esta página resume los estilos que la guía **no** cubre con un laboratorio, para que tengas el mapa completo. La clasificación sigue *Fundamentals of Software Architecture* (Richards y Ford).

| Estilo | Laboratorio |
|---|---|
| Capas | [02](../labs/02-capas-hexagonal-clean/) |
| Monolito modular | [04](../labs/04-monolito-modular/) |
| Event-driven | [06](../labs/06-sync-vs-async/) y 15 a 18 |
| Microservicios | Del [05](../labs/05-microservicios/) en adelante |
| Los de esta página | Solo explicados |

---

## Microkernel (plugins)

**Qué es:** un núcleo mínimo y estable, más plugins que agregan funcionalidad sin tocar el núcleo. El núcleo define un contrato y descubre los plugins (por configuración, por classpath o por un registro).

**Ejemplos reales:** VS Code, Eclipse, navegadores con extensiones, motores de reglas de seguros donde cada producto es un plugin.

**En Mercado de Inventos sería:** un motor de precios cuyas reglas (descuento por volumen, cupones, precio regional) son plugins que se cargan sin redeployar el núcleo.

**Cuándo sí:** el producto se personaliza mucho por cliente o región, y las variantes son independientes entre sí.
**Cuándo no:** cuando los "plugins" necesitan hablar entre ellos todo el tiempo; el contrato del núcleo termina creciendo sin control.

## Pipeline (pipes & filters)

**Qué es:** una cadena de pasos (*filters*) donde cada uno recibe datos, los transforma y los pasa al siguiente por un canal (*pipe*). Los pasos no se conocen entre sí.

**Ejemplos reales:** comandos Unix encadenados con `|`, ETL, procesamiento de imágenes, Kafka Streams.

**En Mercado de Inventos sería:** la importación masiva de inventos: leer CSV → validar → normalizar precios → detectar duplicados → publicar.

**Cuándo sí:** procesamiento por etapas, sobre todo de datos en lote o en flujo.
**Cuándo no:** flujos interactivos con muchas ramas y decisiones; ahí una saga orquestada encaja mejor.

## Basado en servicios (service-based)

**Qué es:** el punto intermedio entre un monolito y los microservicios. Unos pocos servicios grandes, de 4 a 12, uno por área de negocio, que **comparten la base de datos**.

**En Mercado de Inventos sería:** tres servicios (Catálogo, Ventas y Logística) contra una sola base Postgres con esquemas separados.

**Cuándo sí:** quieres despliegues independientes por área, pero sin pagar la consistencia eventual. Es una buena etapa intermedia entre el lab 04 y el lab 05.
**Cuándo no:** cuando los equipos necesitan evolucionar sus esquemas de forma independiente; la base compartida vuelve a acoplarlos.

## Space-based

**Qué es:** elimina la base de datos del camino crítico. Los datos viven replicados **en memoria** en cada unidad de procesamiento (*data grid*), y se escriben a la base de forma asíncrona.

**Ejemplos reales:** venta de entradas para conciertos, subastas online, sistemas con picos extremos y repentinos de tráfico.

**En Mercado de Inventos sería:** el lanzamiento de un invento muy esperado, con 100 000 compradores en el primer minuto.

**Por qué no tiene lab:** necesita un data grid en memoria (Hazelcast, Apache Ignite) y varias instancias coordinadas. Se puede montar en Compose, pero lo que enseñaría (elasticidad ante picos) no se aprecia en una sola máquina.

## SOA orquestada (ESB)

**Qué es:** el estilo de los años 2000. Servicios empresariales reutilizables, conectados por un *Enterprise Service Bus* que enruta, transforma y orquesta.

**Por qué importa hoy:** muchos sistemas legados con los que te vas a integrar son así. Los labs 19 (Anti-Corruption Layer) y 20 (Strangler Fig) enseñan a convivir con ellos y a migrarlos. La lección histórica: poner la lógica de negocio en el bus creó un monolito distribuido. Los microservicios reaccionaron con *smart endpoints, dumb pipes*.

## Serverless

**Qué es:** funciones (o contenedores) que el proveedor de nube levanta bajo demanda y factura por invocación. Ejemplos: AWS Lambda, Google Cloud Run, Azure Functions.

**En Mercado de Inventos sería:** generar el PDF de la factura cuando llega `PaymentCompleted`.

**Por qué no tiene lab:** depende de un proveedor de nube. Los patrones que sí aplican (event-driven, idempotencia, outbox) son los mismos de los labs.

## Service Mesh / Sidecar

**Qué es:** un proxy (*sidecar*) junto a cada servicio que se encarga del tráfico de red: mTLS, reintentos, circuit breakers y métricas. Un *control plane* configura todos los sidecars. Ejemplos: Istio, Linkerd.

**Por qué no tiene lab:** se apoya en Kubernetes. Es un buen candidato para un módulo futuro. En el lab 11 vas a ver que reintentos y circuit breakers se pueden hacer en el código; un mesh los mueve a la infraestructura.
