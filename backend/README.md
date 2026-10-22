# backend/

The Nyx API (Node.js + Fastify, plain JavaScript ES modules). It serves the one config document to every phone, and later takes opt-in daily stats (M14) and hosts the admin console (M11). Design: `docs/ARCHITECTURE.md` §7, §9, §12 and §14.10.

## Endpoints

| Method | Path | What |
|---|---|---|
| GET | `/v1/config?schema=1` | The published config for that schema version. `ETag: "<configVersion>"`; send it back as `If-None-Match` to get `304` when nothing changed. `404` if nothing is published for that schema version |
| GET | `/health` | `200 {"status":"ok"}`, or `503` when the database doesn't answer within 5 s |

Errors always look like `{"error":{"code":"NOT_FOUND","message":"…","details":[…]}}` (codes in ARCHITECTURE §7).

## Layout

```text
src/
├── features/
│   ├── config/    ← GET /v1/config; config.validation.js checks documents like the phone does
│   └── health/    ← GET /health
├── middleware/    ← errorHandler.js (every error → the standard error shape)
├── lib/           ← env.js (checks env vars), db.js (Postgres pool), migrate.js, sharedFiles.js, httpError.js
├── app.js         ← builds the Fastify app (tests use it directly)
└── server.js      ← starts it
migrations/        ← 001_init.sql … applied in name order at start-up
seeds/             ← initial-config.js: publishes shared/default-config.json as version 1 into an empty database
testing/           ← test helpers (not `test/`: Node would run every file there as a test)
```

Tests sit next to the code they test (`*.test.js`). Database tests need `docker compose up -d db` (they use `nyx_test`).

## Changing the database

Add the next numbered file, e.g. `migrations/002_stats.sql`. The server applies it on its next start, inside a transaction; a failing file stops start-up and leaves nothing behind. Never edit a file that has already run anywhere: fix forward with a new one.

## Setup

Needs Node 26.12.1 (`engines` in `package.json`).

```bash
npm ci
cp .env.example .env    # then edit if needed; .env is never committed
```

Every variable is explained in `.env.example` and `docs/ARCHITECTURE.md` §12. Start-up stops with a list of every missing or bad variable.

## Run

Everything in Docker (from the repo root):

```bash
docker compose up --build    # Postgres 18 (port 5432) + the API (port 3000)
docker compose down          # stop; add -v to also delete the database volume
```

Or only the database in Docker and the API on your machine, which restarts on every file change:

```bash
docker compose up -d db
npm run dev                  # reads .env (localhost database)
```

- The `db` container has two databases: `nyx` (the app) and `nyx_test` (`npm test`, emptied before each test). `nyx_test` is created when the volume is first made; on an older volume, run `docker compose down -v` once.
- The image is built from the repo root (`backend/Dockerfile`), so it can include `shared/config.schema.json` and `shared/default-config.json`. `.dockerignore` at the root keeps everything else, tests included, out of it.
- Dev database login: `nyx` / `nyx` — local only.

## Test and lint

```bash
npm test                 # node:test, one file at a time
npm run test:coverage    # same, and fails below 80% line coverage on src/features/
npm run lint             # Biome: lint + format check (npx biome check --write . fixes formatting)
```

The config tests include the contract with the phone: every file in `shared/fixtures/valid/` must be accepted and every file in `shared/fixtures/invalid/` rejected with the rule in its name, exactly as the Android tests do. CI runs lint, tests with coverage and a Docker build on every push that touches `backend/` or `shared/` (`.github/workflows/backend.yml`).
