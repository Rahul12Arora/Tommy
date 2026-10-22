# backend/

The Nyx API (Node.js + Fastify, plain JavaScript ES modules). It serves the one config document to every phone, and later takes opt-in daily stats (M14) and hosts the admin console (M11). Design: `docs/ARCHITECTURE.md` §7, §9, §12 and §14.10.

## Layout

```text
src/
├── features/      ← one folder per feature: routes, service, model, validation, tests
├── middleware/    ← errorHandler.js (every error → the standard error shape)
├── lib/           ← env.js (checks env vars), db.js (Postgres pool), httpError.js
├── app.js         ← builds the Fastify app (tests use it directly)
└── server.js      ← starts it
```

Tests sit next to the code they test (`*.test.js`).

## Setup

Needs Node 26.12.1 (`engines` in `package.json`).

```bash
npm ci
cp .env.example .env    # then edit if needed; .env is never committed
```

Every variable is explained in `.env.example` and `docs/ARCHITECTURE.md` §12. Start-up stops with a list of every missing or bad variable.

## Run

```bash
npm run dev      # restarts on file changes; reads .env
npm start        # what the container runs; env comes from outside
```

The API listens on `PORT` (3000).

## Test and lint

```bash
npm test                 # node:test, one file at a time
npm run test:coverage    # same, and fails below 80% line coverage on src/features/
npm run lint             # Biome: lint + format check (npx biome check --write . fixes formatting)
```
