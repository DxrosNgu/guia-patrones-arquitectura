# syntax=docker/dockerfile:1
# Cada micro-frontend es su propia imagen: se construye y se despliega solo, sin los demás.
# "platform" (design system + contrato de eventos) llega como contexto adicional (ver compose.yaml).
FROM node:22.23.4-alpine AS build
COPY --from=platform . /platform
WORKDIR /src
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginx:1.31.7-alpine
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist /usr/share/nginx/html
HEALTHCHECK --interval=5s --timeout=3s --retries=20 CMD wget -qO- http://localhost/ > /dev/null || exit 1
