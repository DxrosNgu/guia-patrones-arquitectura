#!/usr/bin/env bash
# Experimento: Pedidos usa una clase interna de Catálogo. Compila, pero el build falla.
#
#   ./experiments/romper-limites.sh          # con Docker (no necesitas Java instalado)
#   LOCAL=1 ./experiments/romper-limites.sh  # con tu Java 25 y ./mvnw
set -uo pipefail
cd "$(dirname "$0")/.."

TARGET=shop/orders/src/main/java/com/mercadoinventos/shop/orders/internal/AtajoAlCatalogo.java
cp experiments/AtajoAlCatalogo.java "$TARGET"
trap 'rm -f "$TARGET"; echo "(se quitó $TARGET)"' EXIT
echo "➕ Agregado $TARGET (importa catalog.internal.ProductEntity)"
echo "🔨 Compilando y corriendo ModuleBoundariesTest..."

MVN_ARGS=(-B -q test -pl app -am -Dtest=ModuleBoundariesTest -Dsurefire.failIfNoSpecifiedTests=false)
if [ "${LOCAL:-0}" = 1 ]; then
  (cd shop && ./mvnw "${MVN_ARGS[@]}") > /tmp/lab04-build.log 2>&1
else
  docker run --rm -v "$PWD/shop":/src -v lab04-m2:/root/.m2 -w /src maven:3.9.16-eclipse-temurin-25 \
    mvn "${MVN_ARGS[@]}" > /tmp/lab04-build.log 2>&1
fi
status=$?

grep -E "Architecture Violation|was violated|Method <|Field <|Constructor <" /tmp/lab04-build.log | head -8
if [ $status -ne 0 ] && grep -q "Architecture Violation" /tmp/lab04-build.log; then
  echo "✅ El build FALLÓ por ArchUnit, como debía. Log completo: /tmp/lab04-build.log"
else
  echo "❌ El build no falló por ArchUnit (exit $status). Revisa /tmp/lab04-build.log"; exit 1
fi
