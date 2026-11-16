# syntax=docker/dockerfile:1
#
# UN deployable para todo el monolito: se compilan todos los módulos y se empaqueta app/.

# ── Etapa 1: compilar ───────────────────────────────────────────────────
FROM maven:3.9.16-eclipse-temurin-25 AS build
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests

# ── Etapa 2: ejecutar ───────────────────────────────────────────────────
FROM eclipse-temurin:25.0.5_8-jre-alpine
WORKDIR /app
COPY --from=build /src/app/target/quarkus-app/lib/ ./lib/
COPY --from=build /src/app/target/quarkus-app/*.jar ./
COPY --from=build /src/app/target/quarkus-app/app/ ./app/
COPY --from=build /src/app/target/quarkus-app/quarkus/ ./quarkus/

RUN adduser -D -u 1001 app
USER 1001

EXPOSE 8080
HEALTHCHECK --interval=5s --timeout=3s --start-period=20s --retries=20 \
  CMD wget -qO- http://localhost:8080/q/health/ready > /dev/null || exit 1

CMD ["java", "-XX:MaxRAMPercentage=75", "-jar", "quarkus-run.jar"]
