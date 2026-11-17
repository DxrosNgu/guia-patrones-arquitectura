# syntax=docker/dockerfile:1
# El gateway (Spring Boot). Se construye una vez y se usa igual en todos los ambientes.

# ── Etapa 1: compilar ───────────────────────────────────────────────────
FROM maven:3.9.16-eclipse-temurin-25 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests

# ── Etapa 2: ejecutar ───────────────────────────────────────────────────
FROM eclipse-temurin:25.0.5_8-jre-alpine
WORKDIR /app
COPY --from=build /src/target/gateway.jar ./gateway.jar

RUN adduser -D -u 1001 app
USER 1001

EXPOSE 8080
HEALTHCHECK --interval=5s --timeout=3s --start-period=20s --retries=20 \
  CMD wget -qO- http://localhost:8080/actuator/health > /dev/null || exit 1

CMD ["java", "-XX:MaxRAMPercentage=75", "-jar", "gateway.jar"]
