# Built from the repo root (docker-compose.yml, D-42) so the image can include shared/.
# M11 adds a first stage that builds the admin console into /console/.
FROM node:26.12.1-alpine3.24

# Same layout as the repo, so src/lib/sharedFiles.js finds ../../../shared both here and locally
WORKDIR /app/backend

COPY backend/package.json backend/package-lock.json ./
RUN npm ci --omit=dev

COPY backend/src ./src
COPY backend/migrations ./migrations
COPY backend/seeds ./seeds
COPY shared/config.schema.json shared/default-config.json /app/shared/

USER node
EXPOSE 3000

# Exec form: node is PID 1 and gets SIGTERM itself (server.js shuts down cleanly)
CMD ["node", "src/server.js"]
