# syntax=docker/dockerfile:1
# Frontend imajı: Angular production build → nginx (statik dosyalar + /api → api-gateway proxy).
# Build context repo köküdür (docker-compose.yml ile aynı):
#   docker build -f frontend/Dockerfile .

# ---- build ----
# Node/npm sürümü package.json "engines" ve frontend/.nvmrc ile aynı
FROM node:14.18.1-alpine AS build
WORKDIR /app
ENV NG_CLI_ANALYTICS=false

# Önce yalnızca bağımlılık tanımları: kaynak değişince npm ci katmanı önbellekten gelir
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci --no-audit --no-fund

COPY frontend/ ./
RUN npx ng build --configuration production

# ---- runtime ----
# Root olmayan nginx (8080'i dinler; 80 gibi ayrıcalıklı port açamaz)
FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/todo-frontend /usr/share/nginx/html
EXPOSE 8080
