# syntax=docker/dockerfile:1
# Build en dos etapas: Node solo para compilar; la imagen final es nginx + estáticos.

# ── Etapa 1: compilar ───────────────────────────────────────────────────
FROM node:22.23.4-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# ── Etapa 2: servir ─────────────────────────────────────────────────────
FROM nginx:1.31.7-alpine
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --chmod=755 nginx/40-runtime-config.sh /docker-entrypoint.d/40-runtime-config.sh
COPY --from=build /src/dist /usr/share/nginx/html

HEALTHCHECK --interval=5s --timeout=3s --retries=20 \
  CMD wget -qO- http://localhost/ > /dev/null || exit 1
