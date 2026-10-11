# syntax=docker/dockerfile:1

# ---- Build the SPA ----
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
# Relative API path: the same image works on any host because Nginx proxies /api.
ENV VITE_API_BASE_URL=/api/v1
RUN npm run build

# ---- Serve with Nginx (non-root, listens on 8080) ----
FROM nginxinc/nginx-unprivileged:1.29-alpine AS runtime
# *.template files are rendered with envsubst at start-up (BACKEND_UPSTREAM).
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
ENV BACKEND_UPSTREAM=backend:8080
EXPOSE 8080
