# Ambientes en la guía: local, dev y prod

Esta página explica la estrategia de ambientes que siguen **todos** los labs. El [lab 01](../labs/01-ambientes/) la enseña paso a paso; aquí está el resumen para consultar después.

## La idea en una frase

> Se construye **una** imagen y se despliega en cada ambiente con **otra configuración**. El código nunca pregunta en qué ambiente está.

## Los archivos de cada lab

| Archivo | Para qué | ¿Se versiona? |
|---|---|---|
| `compose.yaml` | Lo que es igual en todos los ambientes. Sin secretos | Sí |
| `compose.override.yaml` | Ambiente **local**. Compose lo carga automáticamente | Sí |
| `compose.dev.yaml` | Ambiente **dev** | Sí |
| `compose.prod.yaml` | Ambiente **prod** | Sí |
| `.env.example` | Valores de local, para copiar a `.env` (opcional) | Sí |
| `.env` | Tu copia local | **No** |
| `.env.dev`, `.env.prod` | Configuración **no secreta** de cada ambiente | Sí |
| `secrets/*.txt.example` | Plantillas de los secretos de prod | Sí |
| `secrets/*.txt` | Secretos de prod | **No** |

## Los comandos

```bash
# local
docker compose up --build -d
```

```bash
# dev: los secretos llegan como variables (en la vida real, del pipeline)
DB_PASSWORD=... docker compose -f compose.yaml -f compose.dev.yaml --env-file .env.dev up -d
```

```bash
# prod: los secretos llegan como archivos
docker compose -f compose.yaml -f compose.prod.yaml --env-file .env.prod up -d
```

Antes de levantar, puedes ver la configuración final ya combinada:

```bash
docker compose -f compose.yaml -f compose.prod.yaml --env-file .env.prod config
```

## Reglas que siguen todos los labs

1. **El tag de la imagen es la versión, no el ambiente:** `mercado/lab01-catalog:1.0.0`, nunca `:prod`.
2. **Dev y prod no compilan:** sus overlays tienen `build: !reset null` y `pull_policy: never`. Si la imagen no existe, fallan en lugar de construir otra a escondidas.
3. **Cada ambiente es un proyecto de Compose distinto** (`name:` en su overlay), con su propia red y sus propios volúmenes. Los datos de local nunca se mezclan con los de prod.
4. **Valores por defecto en `compose.yaml`:** local arranca sin crear ningún archivo.
5. **Fail fast en tres niveles:**
   - Compose: `${VAR:?mensaje}` para variables obligatorias.
   - La app: configuración tipada que se valida al arrancar (`@ConfigMapping`, `pydantic-settings`).
   - El contenedor: el entrypoint falla si un `*_FILE` apunta a un archivo inexistente.
6. **Secretos:** local usa valores falsos, dev usa variables del entorno que despliega y prod usa archivos en `/run/secrets` con la convención `*_FILE`.
7. **Solo lo necesario expuesto:** local expone todo, dev expone servicios y herramientas, y prod expone solo el punto de entrada.

## Qué cambia entre ambientes

| Aspecto | local | dev | prod |
|---|---|---|---|
| Logs | `DEBUG`, texto | `INFO`, JSON | `INFO`, JSON |
| Puertos | todos | servicios y herramientas | solo el punto de entrada |
| Swagger UI | activo | activo | desactivado |
| Secretos | falsos, por defecto | variables obligatorias | archivos |
| Recursos | sin límite | sin límite | límites y `restart: unless-stopped` |
| Kafka (labs con Kafka) | 1 broker, replicación 1 | 1 broker | ⚠️ en la vida real son ≥ 3 brokers con replicación 3. Aquí se explica, pero no se simula |
| Bases de datos | un contenedor, una base por servicio | igual | ⚠️ en la vida real, instancias separadas y administradas |

## Lo que la guía simplifica (y la vida real no)

- **Los tres ambientes en tu máquina.** En la realidad, dev y prod son servidores o clústeres distintos, con redes separadas.
- **Secretos en archivos locales.** En la realidad vienen de un gestor de secretos (Vault, AWS Secrets Manager, Kubernetes Secrets) y rotan.
- **Las imágenes no se publican.** En la realidad, CI construye la imagen, la sube a un registro y cada ambiente la descarga por su *digest*.
- **Sin alta disponibilidad.** Una sola instancia de cada cosa.

Cada lab marca con ⚠️ las simplificaciones que afectan a su patrón.

## Perfiles de Quarkus vs. ambientes

Quarkus tiene los perfiles `dev`, `test` y `prod`, que describen el **modo** de ejecución:

| Perfil | Cuándo se activa | Para qué |
|---|---|---|
| `dev` | `./mvnw quarkus:dev` | Recarga en caliente y Dev Services (BD y Kafka automáticos) |
| `test` | `./mvnw verify` | Tests |
| `prod` | `java -jar` (dentro del contenedor) | Ejecución normal |

Dentro de un contenedor siempre corre el perfil `prod`, **en los tres ambientes**. El ambiente se distingue por variables de entorno. Mezclar los dos conceptos (`QUARKUS_PROFILE=dev` para "el servidor de dev") aplicaría al servidor compartido la configuración pensada para tu máquina.
