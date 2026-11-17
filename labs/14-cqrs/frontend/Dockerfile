# syntax=docker/dockerfile:1
FROM node:22.23.4-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginx:1.31.7-alpine
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist /usr/share/nginx/html
HEALTHCHECK --interval=5s --timeout=3s --retries=20 CMD wget -qO- http://localhost/ > /dev/null || exit 1
