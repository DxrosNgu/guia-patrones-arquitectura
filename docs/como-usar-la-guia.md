# Cómo usar la guía

## 1. Prepara tu máquina

Lo único obligatorio es **Docker con Compose v2.20 o superior**:

```bash
docker compose version
```

Si ves `v2.20` o más (o `v5.x`), estás listo. Asigna a Docker al menos 8 GB de RAM; los labs con Kafka y observabilidad piden más, así que si puedes dale 16 GB.

No necesitas Java, Python ni Node instalados: cada servicio se compila **dentro** de un contenedor. Solo te hacen falta si quieres usar el modo desarrollo (sección 5) o correr los tests fuera de Docker.

## 2. Elige una ruta

Mira las rutas de aprendizaje en el [README principal](../README.md#36-rutas-de-aprendizaje). Si no sabes por dónde empezar, haz el **lab 01**: todos los demás asumen que entiendes cómo se separan los ambientes.

## 3. Trabaja un lab

1. **Lee el problema** en el README del lab antes de levantar nada. Intenta pensar cómo lo resolverías.
2. **Levanta la versión ingenua** si el lab la tiene (`docker compose --profile naive up --build`) y provoca la falla.
3. **Levanta el patrón** (`docker compose up --build`).
4. **Haz los experimentos de "Rómpelo"** en orden. Cada uno dice qué deberías ver. Si ves otra cosa, ¡anótalo! Muchas veces se aprende más de eso.
5. **Prueba los otros ambientes** (sección "¿Qué cambia en dev y prod?").
6. **Haz el ejercicio** antes de pasar al siguiente lab.
7. **Limpia:** `docker compose down -v`.

## 4. Herramientas que ayudan

- **`requests.http`**: cada lab trae las peticiones listas. Ábrelo con la extensión *REST Client* de VS Code o directamente en IntelliJ.
- **Swagger UI**: `http://localhost:<puerto>/q/swagger-ui` (Quarkus) o `http://localhost:<puerto>/docs` (FastAPI).
- **Kafka UI** (`http://localhost:8090`) en los labs con Kafka: ves topics, mensajes y consumidores.
- **Grafana** (`http://localhost:3001`) desde el lab 10: trazas, logs y métricas.
- **`smoke.sh`**: prueba de humo del lab. Si algo no funciona, córrela para saber si el problema es del lab o de tu entorno.

## 5. Modo desarrollo (opcional)

Para cambiar código y verlo al instante, levanta solo la infraestructura con Compose y corre el servicio en tu máquina:

```bash
docker compose up -d postgres            # o la infraestructura que use el lab
cd services/catalog && ./mvnw quarkus:dev
```

```bash
cd services/inventory
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt -r requirements-dev.txt
uvicorn app.main:app --reload --port 8103
```

```bash
cd frontend && npm install && npm run dev
```

Correr los tests:

```bash
cd services/catalog && ./mvnw verify      # Quarkus (usa Docker para la BD de prueba)
cd services/inventory && pytest           # FastAPI
```

## 6. Si algo falla

| Síntoma | Causa probable | Qué hacer |
|---|---|---|
| `port is already allocated` | Quedó levantado otro lab | `docker ps` y `docker compose down -v` en el lab anterior |
| Un servicio reinicia en bucle | Su dependencia no está lista o falta una variable | `docker compose logs <servicio>` |
| `include` no reconocido | Compose anterior a 2.20 | Actualiza Docker Desktop / el plugin de Compose |
| Builds muy lentos la primera vez | Descarga de dependencias (Maven, pip, npm) | Es normal; las siguientes usan la caché de capas |
| Datos raros de una corrida anterior | Volúmenes viejos | `docker compose down -v` y vuelve a levantar |
