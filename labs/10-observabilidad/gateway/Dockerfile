# syntax=docker/dockerfile:1
# El gateway (Spring Boot) + el agente de OpenTelemetry para Java.

# ── Etapa 1: compilar (y bajar el agente) ───────────────────────────────
FROM maven:3.9.16-eclipse-temurin-25 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests

# ── Etapa 2: ejecutar ───────────────────────────────────────────────────
FROM eclipse-temurin:25.0.5_8-jre-alpine
WORKDIR /app
COPY --from=build /src/target/gateway.jar ./gateway.jar
COPY --from=build /src/target/otel/opentelemetry-javaagent.jar ./opentelemetry-javaagent.jar

RUN adduser -D -u 1001 app
USER 1001

EXPOSE 8080
HEALTHCHECK --interval=5s --timeout=3s --start-period=30s --retries=20 \
  CMD wget -qO- http://localhost:8080/actuator/health > /dev/null || exit 1

# -javaagent: el agente se engancha a la JVM ANTES de la aplicación e instrumenta Netty, WebFlux y el
# cliente HTTP del gateway. La aplicación no sabe que existe. Se configura con variables OTEL_*.
CMD ["java", "-XX:MaxRAMPercentage=75", "-javaagent:/app/opentelemetry-javaagent.jar", "-jar", "gateway.jar"]
