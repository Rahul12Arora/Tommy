# Tech stack — v1.0 (approved 2026-10-18)

> Phase 3 output. Decisions (`D-##`) are logged in `docs/DISCUSSION.md`.
> Versions checked on 2026-10-18 against official release pages and the npm registry (sources in §9).
> Nothing outside the dependency register (§6) gets installed. New needs mid-build go through the same approval.

---

## 1. Summary

| Layer | Choice | Version |
|---|---|---|
| Android app | Kotlin + Jetpack Compose, single `:app` module | Kotlin 2.4.21 · Compose BOM 2026.09.00 |
| Android build | Android Gradle plugin + Gradle | AGP 9.4.1 · Gradle 9.6.0 |
| Android SDK levels | min / target / compile | 30 / 36 / 37 |
| Backend | Node.js + Fastify, JavaScript (ES modules) | Node 26 LTS · Fastify 5.12.7 |
| Admin console | React + Vite, forms generated from the config schema | React 19.3.1 · Vite 8.3.4 · RJSF 6.11.0 |
| Shared contract | JSON Schema (draft-07) + test fixtures | — |
| Server database | PostgreSQL | 18 (18.6) |
| Hosting | One Docker container (API + console) + managed Postgres; provider chosen in week 2 | — |
| IDEs | Android Studio (app) · VS Code (backend, console) — both with Claude Code | Android Studio Rabbit 1 |

---

## 2. Why this stack — and what was rejected

**Android app — Kotlin + Jetpack Compose** (D-31)
- The core of the app is Android system features: the overlay window, the always-on service and usage events. Native Kotlin reaches all of them directly.
- Compose is Google's default UI toolkit; Android Studio's emulator, previews and profiler are built around it.
- Rejected **React Native**: the overlay, the service and usage tracking would still need custom Kotlin, so we'd have two languages plus a bridge between them.
- Rejected **Flutter**: the core features depend on community plugins maintained by individuals, plus a new language (Dart).

**Backend — Node.js + Fastify** (D-32)
- Your existing skill set.
- Fastify validates requests against JSON Schema out of the box, the same format that defines the config.
- Rejected **Express**: more familiar, but needs a separate validation library.

**Admin console — React + Vite + forms generated from the schema** (D-33)
- Adding a field to `shared/config.schema.json` adds an input to the console automatically, with range checks. That's the "easy to change" requirement.
- Rejected **hand-built forms**: more code for every setting.
- Rejected **a raw JSON editor**: least work, worst to use.

**Database — PostgreSQL 18** (D-34)
- Every managed host offers it with backups.
- Rejected **SQLite**: zero setup, but you'd manage the file and its backups yourself.

**Hosting — one container + managed Postgres** (D-35)
- The backend serves the built console from the same address, so there's no cross-origin (CORS) setup.
- The provider (e.g. Render, Railway, Fly.io) is chosen in week 2.
- Rejected **a self-managed VPS**: more operations work for a solo developer.

---

## 3. Size tier and repo layout (D-36)

**Standard tier.**
- **Why not Simple:** there are two front ends (the Android app and the admin console) plus a shared contract.
- **Why not Complex:** one backend, no microservices.

```text
project/
├── android/                 ← the app (single :app module, feature packages)
│   └── app/src/main/java/…/ ← full package list: ARCHITECTURE.md §3
│       ├── setup/           ← S-01 to S-07
│       ├── service/         ← PetService, receivers, notifications
│       ├── tracking/        ← usage events, categories, daily totals
│       ├── rules/           ← triggers, nags, breaks, bubble rules
│       ├── mood/
│       ├── focus/
│       ├── pet/             ← overlay: pet, bubble, panel
│       ├── home/            ← S-08, S-10
│       ├── settings/
│       └── config/          ← built-in defaults, fetch, validation, layering
├── backend/                 ← Node API: config, stats, admin login
├── admin/                   ← web admin console
├── shared/                  ← config.schema.json + fixtures only
├── docs/
├── docker-compose.yml       ← backend + Postgres for local development
├── AGENTS.md
└── README.md
```

- **One Gradle module, not several:** fewer files and faster setup. Split later only if build times hurt.
- The exact package path is set in M0 once the app ID is chosen.

---

## 4. Runtimes and toolchain

| Tool | Version | Notes |
|---|---|---|
| Android Studio | Rabbit 1 (current stable) | With the Claude Code plugin |
| Android Gradle plugin | 9.4.1 (CR-06) | Patch release of 9.4.0. Supports up to API 37; requires Gradle ≥ 9.6.0, JDK ≥ 17, Build Tools ≥ 36.0.0 |
| Gradle (wrapper) | 9.6.0 | AGP 9.4's minimum and default |
| JDK | 17 or newer | Use the one bundled with Android Studio |
| Kotlin | 2.4.21 | AGP 9 builds Kotlin itself (no `kotlin-android` plugin) but defaults to Kotlin 2.2.10; we opt into 2.4.21 via the build classpath |
| KSP | 2.3.12 | Needed only for Room's code generator. ✓ Confirmed with Kotlin 2.4.21 + AGP 9.4.1 in M0 (2026-10-19) |
| minSdk / targetSdk / compileSdk | 30 / 36 / 37 | Android 11+ (A-07). Target 36 is Google Play's current requirement; compile 37 is the newest AGP 9.4 supports |
| SDK Build Tools | 36.0.0 | AGP 9.4's minimum and default |
| Node.js | 26 LTS | Node 26 enters LTS in Oct 2026, maintenance Oct 2027, end of life Apr 2029. Pin the exact patch in M0, after the 21 Oct 2026 security release |
| npm | bundled with Node | No extra package manager |
| PostgreSQL | 18 (18.6) | Supported until Nov 2030 |

---

## 5. Key technical choices

**Android app**
- **Dependency injection:** manual, one `AppContainer` class. No Hilt.
- **Network:** Android's built-in `HttpURLConnection` for the two calls (fetch config with ETag, post stats). No OkHttp or Ktor.
- **JSON:** kotlinx.serialization.
- **Local storage:**
  - Room for daily totals and the event log.
  - DataStore (Preferences) for user settings.
  - The cached config is a file written atomically (`AtomicFile` from androidx.core).
- **Overlay:** Compose drawn inside the always-on service's window. A `LifecycleService` provides what Compose needs outside an Activity. Details in the M-level design.
- **Navigation:** Navigation 3. Apps targeting API 36 must support Android's predictive back gesture, which Navigation 3 handles.
- **Consistent emoji** (F-15, R-05):
  - Use emoji2 with Google Play services' downloadable emoji font, and turn on "replace all emoji" so the pet looks the same on every brand.
  - Rejected the bundled font: it adds more than 10 MB to the app.
  - Phones without Play services fall back to their own emoji.

**Config validation on the phone**
- The phone reads the config into typed Kotlin classes (kotlinx.serialization) and runs a small Kotlin validator for ranges and cross-field rules (e.g. default within min–max).
- Rejected JSON-Schema libraries on Android: they're heavy (they pull in Jackson).
- **Keeping both sides in sync:** `shared/fixtures/` holds valid and invalid example configs. The backend tests check them with the schema; the Android tests check them with the Kotlin validator. Both must accept and reject the same files.
- This replaces the Phase 2 note that "the same schema file validates on the phone".

**Backend**
- **Admin login:**
  - One admin. The password hash lives in an environment variable.
  - Hashed with **scrypt, built into Node's `crypto`**. AGENTS.md names bcrypt or argon2; scrypt is an equally slow, memory-hard hash and needs no native add-on. Needs your OK.
  - The session is a signed, `httpOnly`, `Secure`, `SameSite=Strict` cookie lasting 12 hours.
  - Login is limited to 5 attempts per minute per IP.
- **Database access:** `pg` with parameterised queries only.
  - Migrations are numbered `.sql` files plus a ~30-line runner. No migration library.
- **Configuration:** Node's built-in `--env-file`. No dotenv.
- **Logging:** Fastify's built-in logger (pino), JSON to stdout, with request IDs.
- **Language:** plain JavaScript (ES modules), not TypeScript. It matches your skills and needs no build step, and Fastify and the schema validate data at runtime anyway. Needs your OK.
  - The alternative is TypeScript 7: more safety, plus one dev dependency and a typecheck step.

**Tests**
- **Android:**
  - JUnit 4 + coroutines-test for rules, mood, nags, breaks and config validation.
  - Compose UI tests for setup and Home.
  - Manual QA on the emulator plus one real phone.
- **Backend:** Node's built-in test runner + Fastify's in-process request injection. Database tests run against the Postgres in Docker Compose.
- **Admin console:** build + lint only in v1. Its logic is the schema, which the backend tests cover.
- **Lint and format:**
  - Android Lint (built in). Warnings fail the build; only the "newer version available" checks are off, because versions change through approval (configured in `android/app/build.gradle.kts`).
  - Biome for all JavaScript: one tool for lint and format.
- **CI:** GitHub Actions — one workflow for Android (build, unit tests, lint) and one for Node (lint, tests, console build). Action versions are pinned in M0.

---

## 6. Dependency register

### Android app (`android/`)

**Build plugins**

| Package | Version | Layer | Purpose | Cost without it | License |
|---|---|---|---|---|---|
| `com.android.application` (AGP) | 9.4.1 (CR-06) | Build | Builds the app | No Android build | Apache-2.0 |
| `org.jetbrains.kotlin:kotlin-gradle-plugin` | 2.4.21 | Build | Use Kotlin 2.4 instead of AGP's default 2.2.10 | Older compiler than the libraries below are built with | Apache-2.0 |
| `org.jetbrains.kotlin.plugin.compose` | 2.4.21 | Build | Compose compiler | No Compose | Apache-2.0 |
| `org.jetbrains.kotlin.plugin.serialization` | 2.4.21 | Build | Generates JSON parsers for config classes | Hand-written parsing for a large config | Apache-2.0 |
| `com.google.devtools.ksp` | 2.3.12 | Build | Runs Room's code generator | No Room | Apache-2.0 |

**Runtime**

| Package | Version | Layer | Purpose | Cost without it | License |
|---|---|---|---|---|---|
| `androidx.core:core-ktx` | 1.19.1 | App | Notifications, `AtomicFile`, Kotlin helpers | Verbose platform code for notifications and safe file writes | Apache-2.0 |
| `androidx.activity:activity-compose` | 1.13.0 | App | Hosts Compose screens in an Activity | No Compose screens | Apache-2.0 |
| `androidx.compose:compose-bom` | 2026.09.00 | App | Keeps all Compose versions aligned | Manual version juggling | Apache-2.0 |
| `androidx.compose.ui:ui` (+ `ui-tooling-preview`) | via BOM (1.12.1) | App | Compose UI | — | Apache-2.0 |
| `androidx.compose.foundation:foundation` | via BOM (1.12.1) | App | Layouts, gestures (drag the pet) | — | Apache-2.0 |
| `androidx.compose.material3:material3` | via BOM (1.4.0) | App | Standard components for app screens | Building buttons, sliders and dialogs from scratch | Apache-2.0 |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.11.0 | App | Lifecycle-aware state in Compose | Leaky collectors | Apache-2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.11.0 | App | ViewModels for screens | Screen state lost on rotation | Apache-2.0 |
| `androidx.lifecycle:lifecycle-service` | 2.11.0 | App | Lets Compose run in the overlay service | Hand-written lifecycle plumbing | Apache-2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-navigation3` | 2.11.0 (CR-08) | App | Scopes each screen's ViewModel to its back-stack entry | ViewModels live as long as the Activity: stale state, S-05's icons kept in memory | Apache-2.0 |
| `androidx.navigation3:navigation3-runtime` | 1.2.0 | App | Back stack for 11 screens | Hand-rolled navigation | Apache-2.0 |
| `androidx.navigation3:navigation3-ui` | 1.2.0 | App | Shows screens + predictive back | Predictive back by hand (required at target 36) | Apache-2.0 |
| `androidx.room:room-runtime` | 2.8.5 | Data | Daily totals + event log (SQLite) with Flow support | Raw SQL boilerplate, no compile-time query checks | Apache-2.0 |
| `androidx.room:room-compiler` (KSP) | 2.8.5 | Build | Generates Room code | — | Apache-2.0 |
| `androidx.datastore:datastore-preferences` | 1.2.1 | Data | User settings, observable | SharedPreferences: synchronous, no change stream | Apache-2.0 |
| `androidx.emoji2:emoji2` | 1.7.0 | App | Same emoji look on every phone brand | Pet looks different per brand (R-05) | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.11.0 | Data | Parse config, stats payload | Hand-written JSON parsing | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.11.0 | App | Background work, timers, flows | Callback-based threading | Apache-2.0 |

**Debug and test only**

| Package | Version | Layer | Purpose | Cost without it | License |
|---|---|---|---|---|---|
| `androidx.compose.ui:ui-tooling` | via BOM | Debug | Previews in Android Studio | No previews | Apache-2.0 |
| `androidx.compose.ui:ui-test-manifest` | via BOM | Debug | Needed by Compose UI tests | — | Apache-2.0 |
| `junit:junit` | 4.13.2 | Test | Unit tests | No unit tests | EPL-1.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | 1.11.0 | Test | Test timers and flows with fake time | Slow, flaky time-based tests | Apache-2.0 |
| `androidx.test:core` | 1.7.0 | Test | Android test helpers | — | Apache-2.0 |
| `androidx.test:runner` | 1.7.0 | Test | Runs on-device tests | No device tests | Apache-2.0 |
| `androidx.test.ext:junit` | 1.3.0 | Test | JUnit 4 rules for device tests | — | Apache-2.0 |
| `androidx.compose.ui:ui-test-junit4` | via BOM | Test | Compose UI tests | No UI tests | Apache-2.0 |
| `androidx.test.espresso:espresso-core` | 3.7.0 (CR-07) | Test | Not used directly: overrides the Espresso 3.5.0 that `ui-test-junit4` brings in, which calls `InputManager.getInstance` and fails on API 35+ (fixed in 3.7.0 per the AndroidX Test release notes) | Device tests can't run on API 35+ emulators or current phones | Apache-2.0 |

### Backend (`backend/`)

| Package | Version | Layer | Purpose | Cost without it | License |
|---|---|---|---|---|---|
| `fastify` | 5.12.7 | API | HTTP server, schema validation, logging | Hand-rolled routing, validation and logging | MIT |
| `@fastify/cookie` | 11.1.3 | API | Signed admin session cookie | Hand-rolled cookie signing (security-sensitive) | MIT |
| `@fastify/rate-limit` | 11.2.1 | API | Limits on login and stats endpoints (NFR) | Hand-rolled limiter | MIT |
| `@fastify/helmet` | 13.2.0 | API | Security headers for the console | Setting a dozen headers by hand | MIT |
| `@fastify/static` | 10.1.6 | API | Serves the built console from the same address | A second host + CORS setup | MIT |
| `pg` | 8.23.1 | Data | PostgreSQL driver | No database access | MIT |
| `@biomejs/biome` (dev) | 2.5.15 | Tooling | Lint + format | Two tools (ESLint + Prettier) | MIT OR Apache-2.0 |

Built into Node instead of packages: test runner (`node:test`), password hashing (`crypto.scrypt`), IDs (`crypto.randomUUID`), env files (`--env-file`), `fetch`.

### Admin console (`admin/`)

| Package | Version | Layer | Purpose | Cost without it | License |
|---|---|---|---|---|---|
| `react` | 19.3.1 | UI | UI library | — | MIT |
| `react-dom` | 19.3.1 | UI | Renders React in the browser | — | MIT |
| `@rjsf/core` | 6.11.0 | UI | Forms generated from the config schema | Hand-built form for every setting | Apache-2.0 |
| `@rjsf/utils` | 6.11.0 | UI | Required by `@rjsf/core` | — | Apache-2.0 |
| `@rjsf/validator-ajv8` | 6.11.0 | UI | Validates the form against the schema in the browser | Errors only after saving | Apache-2.0 |
| `vite` (dev) | 8.3.4 | Tooling | Dev server + production build | Manual bundling | MIT |
| `@vitejs/plugin-react` (dev) | 6.1.2 | Tooling | JSX support in Vite | No JSX | MIT |
| `@biomejs/biome` (dev) | 2.5.15 | Tooling | Lint + format | — | MIT OR Apache-2.0 |

**Not added on purpose:**
- **A router:** four screens; a simple state switch is enough.
- **An HTTP client:** `fetch` is built in.
- **A CSS framework:** plain CSS.
- **A chart library:** the stats screen (W-04, week 2–3) starts with simple SVG. A library goes through approval only if that isn't enough.

### Shared (`shared/`)

- No packages. Contains `config.schema.json` (JSON Schema draft-07), `fixtures/valid/*.json` and `fixtures/invalid/*.json`.

### Infrastructure

| Item | Version | Purpose |
|---|---|---|
| Docker image `node` | 26-alpine (exact tag pinned in M0) | Backend container |
| Docker image `postgres` | 18-alpine | Local database |
| GitHub Actions: `actions/checkout` | v7.0.1 (pinned by commit SHA) | CI |
| GitHub Actions: `actions/setup-java` (Temurin JDK 25) | v6.1.0 (SHA) | CI |
| GitHub Actions: `gradle/actions/setup-gradle` | v6.4.0 (SHA), `cache-provider: basic` — the default "enhanced" cache is a commercial service | CI |
| GitHub Actions: `actions/setup-node` | pinned in M10 | CI |

---

## 7. Approved 2026-10-18

1. The stack, the size tier and every dependency above (Phase 3 exit gate) — D-31 to D-36.
2. JavaScript, not TypeScript, for the backend and console (§5) — D-37.
3. scrypt (built into Node) for the admin password instead of bcrypt/argon2 (§5) — D-38.
4. Phone-side config validation via typed parsing + Kotlin validator + shared fixtures (§5) — D-39.
5. emoji2 with the downloadable font instead of bundling a >10 MB font (§5) — D-40.

---

## 8. To confirm during M0 (couldn't be verified from docs today)

- ~~KSP 2.3.12 builds with Kotlin 2.4.21 under AGP 9.4.~~ ✓ Confirmed 2026-10-19: Room generated a database and DAO through KSP; no fallback needed.
- Node 26's LTS promotion and the 21 Oct security release → pin the exact patch.
- ~~emoji2 "replace all" works for Compose text in the overlay window.~~ ✓ Confirmed 2026-10-21 (M5): the app initialises emoji2 itself (D-76); the font loads from Google Play services, also after a reboot with no Activity, and Compose text in the overlay and the app uses it.
- Restarting the service after a reboot on Android 15+ (R-09) → handled when that milestone's design is written.

---

## 9. Commands (for the Project card, once M0 exists)

| Task | Android (`android/`) | Backend (`backend/`) | Console (`admin/`) |
|---|---|---|---|
| Install | Android Studio sync | `npm ci` | `npm ci` |
| Run (dev) | Run ▶ in Android Studio, or `./gradlew installDebug` | `docker compose up` (from repo root) | `npm run dev` |
| Test | `./gradlew test` | `npm test` | — |
| Lint | `./gradlew lint` | `npx biome check` | `npx biome check` |
| Build | `./gradlew assembleRelease` | Docker image | `npm run build` |

---

## 10. Sources (checked 2026-10-18)

- Kotlin releases — https://kotlinlang.org/docs/releases.html
- AndroidX latest versions — https://developer.android.com/jetpack/androidx/versions
- Compose BOM mapping — https://developer.android.com/develop/ui/compose/bom/bom-mapping
- AGP 9.4.0 release notes — https://developer.android.com/build/releases/agp-9-4-0-release-notes
- AGP 9.0 release notes (built-in Kotlin) — https://developer.android.com/build/releases/agp-9-0-0-release-notes
- Room releases — https://developer.android.com/jetpack/androidx/releases/room
- Navigation 3 releases — https://developer.android.com/jetpack/androidx/releases/navigation3
- emoji2 guide — https://developer.android.com/develop/ui/views/text-and-emoji/emoji2
- AndroidX Test releases — https://developer.android.com/jetpack/androidx/releases/test
- Android Studio releases — https://developer.android.com/studio/releases
- KSP releases — https://github.com/google/ksp/releases
- kotlinx.serialization releases — https://github.com/Kotlin/kotlinx.serialization/releases
- kotlinx.coroutines releases — https://github.com/Kotlin/kotlinx.coroutines/releases
- JUnit 4 releases — https://github.com/junit-team/junit4/releases
- Node.js releases — https://nodejs.org/en/about/previous-releases
- Node.js release schedule change — https://nodejs.org/en/blog/announcements/evolving-the-nodejs-release-schedule
- PostgreSQL versioning — https://www.postgresql.org/support/versioning/
- npm registry (`npm view <package> version license`) for every backend and console package
