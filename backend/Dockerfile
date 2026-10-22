# Built from the repo root (docker-compose.yml, D-42) so the image can include shared/ and the admin console.

# Stage 1: the admin console as static files (admin/ has no server and no Dockerfile of its own)
FROM node:26.12.1-alpine3.24 AS console
WORKDIR /app/admin
COPY admin/package.json admin/package-lock.json ./
RUN npm ci
COPY admin/index.html admin/vite.config.js ./
COPY admin/scripts ./scripts
COPY admin/src ./src
COPY shared/config.schema.json /app/shared/
RUN npm run build

# Stage 2: the API, which also serves the console at /console/
FROM node:26.12.1-alpine3.24

# Same layout as the repo, so src/lib/sharedFiles.js finds ../../../shared both here and locally
WORKDIR /app/backend

COPY backend/package.json backend/package-lock.json ./
RUN npm ci --omit=dev

COPY backend/src ./src
COPY backend/migrations ./migrations
COPY backend/seeds ./seeds
COPY shared/config.schema.json shared/default-config.json /app/shared/
# server.js serves this folder when it exists
COPY --from=console /app/admin/dist ./console

USER node
EXPOSE 3000

# Exec form: node is PID 1 and gets SIGTERM itself (server.js shuts down cleanly)
CMD ["node", "src/server.js"]
