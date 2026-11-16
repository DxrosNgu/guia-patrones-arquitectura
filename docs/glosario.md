# Glosario

Términos que aparecen en varios labs. Cada lab explica los suyos en detalle.

| Término | Significado |
|---|---|
| **Agregado** (*aggregate*) | Grupo de objetos que se modifica como una unidad y protege sus reglas (invariantes). Ejemplo: un Pedido con sus líneas. Lab 03. |
| **Al menos una vez** (*at-least-once*) | Garantía de entrega: un mensaje nunca se pierde, pero puede llegar repetido. Es la garantía normal de Kafka. Por eso existe el lab 12. |
| **Bounded context** | Frontera dentro de la cual un término del negocio tiene un solo significado. "Invento" no significa lo mismo en Catálogo que en Envíos. Lab 03. |
| **CDC** (*Change Data Capture*) | Leer los cambios de una base de datos desde su log de transacciones (el WAL en Postgres). Lab 15. |
| **Compensación** | Acción que deshace semánticamente un paso ya confirmado: reembolsar en lugar de "borrar el cobro". Labs 16 y 17. |
| **Consistencia eventual** | Los datos de distintos servicios no coinciden en todo momento, pero convergen si no hay cambios nuevos. Lab 14. |
| **Consumer group** | Grupo de consumidores de Kafka que se reparten las particiones de un topic. Lab 06. |
| **DLQ** (*dead letter queue*) | Cola o topic donde van los mensajes que no se pudieron procesar tras varios intentos. Lab 06. |
| **Dual write** | Escribir en dos sistemas (BD y broker) sin una transacción común. Es la causa del lab 15. |
| **Envelope** | Estructura común de todos los eventos de la guía: `eventId`, `type`, `aggregateId`, `occurredAt`, `payload`. |
| **Healthcheck** | Endpoint o comando que dice si un servicio está vivo (*liveness*) o listo para recibir tráfico (*readiness*). Lab 10. |
| **Idempotencia** | Repetir una operación produce el mismo resultado que hacerla una vez. Lab 12. |
| **Invariante** | Regla de negocio que siempre debe cumplirse. Ejemplo: "el total es la suma de las líneas". |
| **KRaft** | Modo de Kafka que no necesita ZooKeeper. Desde Kafka 4.0 es el único modo. |
| **Overlay de Compose** | Archivo que se combina con `compose.yaml` para cambiar o agregar configuración (`-f compose.yaml -f compose.prod.yaml`). Lab 01. |
| **Partición** | Subdivisión de un topic de Kafka. El orden se garantiza solo dentro de una partición; por eso la key de los eventos es el id del agregado. |
| **Pivote** | En una saga, el paso a partir del cual ya no se compensa: solo se avanza. Labs 16 y 17. |
| **Proyección / read model** | Vista de datos construida a partir de eventos, optimizada para consultar. Labs 14 y 18. |
| **Semantic lock** | Un estado intermedio (por ejemplo, `PENDING`) que indica que un registro está en medio de una saga. Labs 16 y 17. |
| **Twelve-Factor** | Doce principios para aplicaciones desplegables en la nube. La guía usa sobre todo los de configuración y paridad entre ambientes. Lab 01. |
