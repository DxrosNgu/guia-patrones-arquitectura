# syntax=docker/dockerfile:1
#
# Build en dos etapas: la imagen final NO lleva Maven ni el código fuente.
# Esta imagen se construye UNA vez y se usa igual en local, dev y prod.

# ── Etapa 1: compilar ───────────────────────────────────────────────────
FROM maven:3.9.16-eclipse-temurin-25 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
# La caché de ~/.m2 acelera los builds siguientes (requiere BuildKit, activo por defecto).
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests

# ── Etapa 2: ejecutar ───────────────────────────────────────────────────
FROM eclipse-temurin:25.0.5_8-jre-alpine
WORKDIR /app
COPY --from=build /src/target/quarkus-app/lib/ ./lib/
COPY --from=build /src/target/quarkus-app/*.jar ./
COPY --from=build /src/target/quarkus-app/app/ ./app/
COPY --from=build /src/target/quarkus-app/quarkus/ ./quarkus/
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh

# No correr como root dentro del contenedor.
RUN adduser -D -u 1001 app
USER 1001

EXPOSE 8080
HEALTHCHECK --interval=5s --timeout=3s --start-period=20s --retries=20 \
  CMD wget -qO- http://localhost:8080/q/health/ready > /dev/null || exit 1

ENTRYPOINT ["docker-entrypoint.sh"]
CMD ["java", "-XX:MaxRAMPercentage=75", "-jar", "quarkus-run.jar"]
