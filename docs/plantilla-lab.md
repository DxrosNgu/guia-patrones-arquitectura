# Plantilla de laboratorio

Todos los labs siguen esta estructura. Si vas a agregar un lab nuevo, copia esta plantilla.

## Archivos

```
labs/NN-nombre-del-patron/
├── README.md                ← sigue las secciones de abajo
├── compose.yaml             ← base: `name:`, include de infra/ y servicios del lab
├── compose.override.yaml    ← ambiente local (Compose lo carga solo)
├── compose.dev.yaml         ← ambiente dev
├── compose.prod.yaml        ← ambiente prod
├── .env.example             ← copiar a .env (opcional: compose.yaml trae valores por defecto)
├── .env.dev                 ← configuración NO secreta de dev (versionada)
├── .env.prod                ← configuración NO secreta de prod (versionada)
├── secrets/                 ← solo si el lab usa secretos en prod (*.example versionados)
├── requests.http            ← peticiones listas para VS Code REST Client o IntelliJ
├── smoke.sh                 ← prueba de humo: verifica que el lab funciona de punta a punta
└── services/
    └── <servicio>/          ← proyecto Quarkus (pom.xml) o FastAPI (pyproject.toml + uv.lock)
```

## Reglas

- **Lo compartido entre labs va en [`shared/`](../shared/)** y se usa con `additional_contexts` de Compose (`COPY --from=shared …`). Si un lab necesita una versión distinta, lleva su propia copia.
- **Frontends en TypeScript** y **servicios Python con uv**.
- **`name:`** al inicio de `compose.yaml` con el formato `labNN-nombre`. Así los contenedores de un lab no se mezclan con los de otro.
- **Valores por defecto** en `compose.yaml` (`${VAR:-valor}`): el lab arranca aunque no hayas copiado `.env`.
- **Healthchecks** en todo servicio del que otro dependa, y `depends_on: condition: service_healthy`.
- **Versiones fijadas** en imágenes y dependencias. Nunca `latest`.
- **Puertos** según la convención del README principal (sección 3.7).
- **Perfil `naive`** cuando el lab muestra la versión ingenua: `docker compose --profile naive up`.

## Secciones del README del lab

```markdown
# NN · Nombre del patrón

> Una frase: qué problema resuelve.

**Patrones:** … · **Stack:** … · **Infra:** … · **Tiempo estimado:** … · **Requiere:** labs …

## El problema
Situación concreta del dominio Mercado de Inventos. Qué duele y por qué.

## La versión ingenua
Cómo se resolvería "a lo directo", cómo levantarla y qué falla.

## El patrón
Qué es, con un diagrama (mermaid) y las piezas clave del código (enlaces a los archivos).

## Arquitectura del lab
Servicios, puertos y flujo.

## Levántalo
Comandos exactos y URLs.

## Rómpelo
Experimentos numerados: qué haces, qué deberías ver, por qué.

## ¿Qué cambia en dev y prod?
Qué configuración cambia y por qué, con los comandos para levantar cada ambiente.

## Costo y cuándo NO usarlo
Complejidad que agrega, alternativas más simples.

## Ejercicio
Una extensión para hacer por tu cuenta, con una pista.

## Para profundizar
Referencias concretas.
```
