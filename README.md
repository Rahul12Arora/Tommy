# Nyx

An emoji pet that floats over every app on an Android phone. Nyx watches which apps you use, warns you when you cross your own limits, nudges you to take breaks, keeps you company during focus sessions — and its mood shows how your day is going.

- **Android app** (Kotlin + Jetpack Compose) — does all tracking and reacting on the phone, offline.
- **Backend** (Node.js + Fastify + PostgreSQL) — serves one tunable config to every phone and collects opt-in anonymous stats. *Week 2.*
- **Admin console** (React) — the developer's web page for tuning limits, lines, pets and switches. *Week 2.*

## Status

Phase 5 — Build. Current milestone and next step: [`docs/PROGRESS.md`](docs/PROGRESS.md).

## Start here

| Read | For |
|---|---|
| [`AGENTS.md`](AGENTS.md) | How this project is built (phases, rules, quick commands for AI agents) |
| [`docs/REQUIREMENTS.md`](docs/REQUIREMENTS.md) | What Nyx does — stories and acceptance criteria |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | How it works and where everything lives (repo map in §13) |
| [`docs/ROADMAP.md`](docs/ROADMAP.md) | Milestones, test strategy, timeline |
| [`docs/TECH_STACK.md`](docs/TECH_STACK.md) | Pinned versions and every dependency |
| [`docs/DISCUSSION.md`](docs/DISCUSSION.md) | Flows, screens, state machines, decisions, open questions |

## Repo layout

```text
android/   ← the Android app            (M0)
backend/   ← Node API + serves console  (M10)
admin/     ← web admin console          (M11)
shared/    ← config schema, defaults, test fixtures
docs/      ← the docs above
```

## Setup

**Android app** (from M0)
- Android Studio Rabbit 1 or newer (bundles the JDK).
- Open the `android/` folder in Android Studio and let Gradle sync.
- Run ▶ on an emulator or a USB-connected phone with developer options and USB debugging on.
- Terminal builds, all test commands and coverage: [`android/README.md`](android/README.md).

**Backend** (from M10)
- Node 26.12.1 and Docker Desktop.
- `docker compose up --build` (from the repo root) starts Postgres and the API on port 3000.
- Running the API on the host, tests and lint: [`backend/README.md`](backend/README.md).

**Console** — instructions arrive with M11.

## Test

| Part | Command | From |
|---|---|---|
| Android unit tests | `./gradlew test` | M0 |
| Android lint | `./gradlew lint` | M0 |
| Backend | `npm test` (in `backend/`) | M10 |

## Deploy

Arrives with M15 (one container + managed PostgreSQL; Google Play).
