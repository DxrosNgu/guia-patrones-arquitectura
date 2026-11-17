# Actualizaciones futuras (opcionales)

La guía usa **solo versiones estables** (releases). Cuando salga la versión final de cada una de estas, se puede actualizar. Hasta entonces, son **opcionales y están pendientes**.

| Componente | Versión actual en la guía | Pendiente | Cuándo actualizar | Qué cambiar |
|---|---|---|---|---|
| **Quarkus** | 3.40.1 | Quarkus 4.0 (al 17-11-2026, en *CR1*) | Cuando salga 4.0.0 **final** | `quarkus.platform.version` en cada `pom.xml`. Revisar la guía de migración (cambios de nombres de extensiones y de propiedades) |
| **Spring Boot** (gateway, labs 07 y 20) | 4.0.9 | Spring Boot 4.1 (4.1.2 ya es estable) | Cuando el release train de Spring Cloud que lo soporta (2026.0) sea final: Spring Cloud 2025.1 solo soporta Boot 4.0 | `spring-boot-starter-parent` y `spring-cloud.version` en el `pom.xml` del gateway |
| **Spring Cloud** | 2025.1.4 | 2026.0 (al 17-11-2026, en RC2) | Cuando salga 2026.0.0 final | `spring-cloud.version` |
| **SQLAlchemy** (lab 10) | 2.0.57 | 2.1.x | Cuando `opentelemetry-instrumentation-sqlalchemy` soporte 2.1 | `pyproject.toml` del inventario del lab 10 |

## Cómo actualizar sin romper nada

1. Cambia la versión **en un lab**.
2. Corre sus tests (`./mvnw verify`, `uv run pytest`, `npm test`).
3. Levanta el lab y corre su `./smoke.sh`.
4. Si todo pasa, replica el cambio en los demás labs y repite.

## Proyecto aparte: los servicios de tu ecosistema

La actualización de tus propios microservicios a la **última versión estable de Spring Boot** es un proyecto separado de esta guía.
