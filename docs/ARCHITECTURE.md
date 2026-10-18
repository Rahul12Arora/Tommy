# Architecture — Nyx · v1.0 HLD (approved 2026-10-18)

> Phase 4 output. Read this first if you're new to the repo: after ~10 minutes you should know how data moves through the system and where every piece lives.
> Low-level design (LLD) for each module is added to §14 at the **start** of its milestone, before any code.
> Rule: code and diagrams must agree. A task that changes a component, a flow or a folder updates this file in the same task.
> Last updated: 2026-10-18

---

## 1. System overview

- **What it does:** an emoji pet floats over every app on an Android phone. It watches which apps are used, warns when the user's limits are crossed, nudges breaks, keeps the user company during focus sessions, and its mood reflects the user's habits.
- **Three deployable parts:**
  - **The Android app.** Does all the tracking and reacting, on the phone, offline.
  - **The backend container.** Serves one config document to every phone, receives opt-in anonymous stats, and serves the admin console. Week 2.
  - **A managed PostgreSQL database.** Config versions and stats. Week 2.

| Who / what | Uses the system to… | Talks to |
|---|---|---|
| User (adult, 18+) | See their usage, set limits and goals, run focus sessions | The app only |
| Admin (the developer) | Tune every number, line, pet and switch; read aggregated stats | The web console |
| Android system services | Usage events, overlay windows, notifications, installed-app info | The app |
| Google Play services | Downloadable emoji font (D-40) | The app |
| Hosting platform | Runs the container, HTTPS, managed Postgres, backups | Backend |

---

## 2. Component diagram

Nodes turn green (`:::done`) as each component lands.

```mermaid
flowchart LR
  subgraph PHONE["Android phone"]
    subgraph APP["Our app — one process"]
      SCR["App screens<br/>MainActivity + Compose<br/>setup · home · focus · trends · settings"]:::pending
      subgraph SVC["PetService — always-on foreground service"]
        TRK["Usage tracker"]:::pending
        RUL["Rules engine<br/>limits · nags · breaks · focus · idle"]:::pending
        MOOD["Mood"]:::pending
        BUB["Bubble arbiter + line picker"]:::pending
        OVL["Overlay UI<br/>pet · bubble · panel"]:::pending
        NTF["Notifier"]:::pending
      end
      CFG["ConfigManager<br/>built-in → admin → user"]:::pending
      STORE[("Room · DataStore<br/>config cache file")]:::pending
      NET["ConfigClient · StatsUploader<br/>week 2"]:::pending
    end
    OS["Android system services<br/>usage events · overlay windows<br/>notifications · package info"]
    GPS["Google Play services<br/>emoji font"]
  end
  subgraph HOST["Hosting — one container + managed Postgres"]
    API["Fastify API<br/>/v1 · /admin · /health"]:::pending
    STATIC["Console files<br/>/console/"]:::pending
    PG[("PostgreSQL")]:::pending
  end
  BROWSER["Admin's browser<br/>React console"]:::pending
  SHARED["shared/<br/>schema · default config · fixtures"]:::pending

  TRK --> RUL
  RUL --> MOOD
  RUL --> BUB
  MOOD -->|"face"| OVL
  BUB --> OVL
  BUB -->|"while hidden"| NTF
  SCR -->|"writes settings and focus requests"| STORE
  STORE -->|"Flows"| SCR
  SVC -->|"usage, events"| STORE
  STORE -->|"focus requests, settings"| SVC
  CFG --> SVC
  CFG --> SCR
  TRK -->|"reads usage events"| OS
  OVL -->|"draws over other apps"| OS
  NTF --> OS
  OVL -.->|"emoji font"| GPS
  NET -->|"HTTPS GET /v1/config<br/>POST /v1/stats"| API
  NET --> CFG
  BROWSER -->|"HTTPS /admin/*"| API
  BROWSER -->|"loads"| STATIC
  API --> PG
  SHARED -.->|"build and test time"| CFG
  SHARED -.->|"build and test time"| API
  SHARED -.->|"build time"| BROWSER

  classDef pending fill:#fff4ce,stroke:#b8860b,color:#222
  classDef done fill:#d8f3dc,stroke:#2d6a4f,color:#222
```

---

## 3. The phone app — how it's organised

**Two entry points, one shared container**
- `MainActivity` hosts the app screens (setup, home, focus setup, trends, settings).
- `PetService` is the always-on foreground service. It runs the engine and draws the pet.
- Both get their repositories from one `AppContainer`, created in `PetApp` (the `Application` class). Dependency injection is manual (TECH_STACK §5).

**Screens never call the service** (D-44)
- Screens write what the user wants into storage: settings, "start a focus session", "show the pet again".
- The service observes storage and reacts.
- So the app works the same whether the service is running, restarting or freshly booted. There's no binding or IPC to get wrong.
- Buttons on the overlay (snooze, hide, dismiss) live inside the service and go straight to the engine.

**Nothing important lives only in memory**
- Usage totals, the event log, focus sessions and settings are stored (Room, DataStore).
- After the service restarts, the engine rebuilds today's state from today's totals and events. Mood is always recomputed from the event log (H-03).

**One engine coroutine owns engine state**
- Ticks, overlay button presses and storage changes are all processed one at a time on a single coroutine.
- So there are no race conditions between, say, a snooze tap and a tick.

**Packages** (inside `android/app/src/main/java/com/companionstudio/nyx/`)

| Package | Owns |
|---|---|
| (root) | `PetApp`, `AppContainer`, `MainActivity`, `AppNavigation` |
| `ui/` | Theme, shared composables |
| `data/` | Room database class, DataStore, `DayClock` (day keys, reset hour) |
| `config/` | Config models, validator, built-in loader, layering, cache; `ConfigClient` (week 2) |
| `setup/` | S-01 to S-07 |
| `home/` | S-08 Home, S-10 Trends |
| `settings/` | S-11 Settings |
| `focus/` | S-09 Focus setup, focus sessions (SM-04) |
| `service/` | `PetService`, boot and screen-on/off receivers, notifications (N-01 to N-04) |
| `tracking/` | Usage events → foreground app → per-app/category totals, stretches, app categories |
| `rules/` | Triggers, nags (SM-02), breaks (SM-03), idle lines, bubble arbiter, line picker |
| `mood/` | Mood calculation (SM-01), bands → pet face |
| `pet/` | Overlay windows: pet, speech bubble, summary panel |
| `stats/` | Daily aggregates + uploader (week 2) |

Each feature package holds its own Room entities and DAOs, repository, ViewModels and screens.

---

## 4. The engine (core loop)

**Tick**
- Runs only while the screen is on. A screen-on/off receiver starts and stops it.
- Interval is adaptive (H-01):
  - ~5 s when a limit is close, a nag is active or a focus session is running.
  - ~30 s otherwise.
  - Both numbers are config.
- Each tick:
  1. Read usage events since the last tick (activity resumed/paused, screen on/off).
  2. Work out the foreground app and credit the elapsed seconds to `(day, package, category at that moment)`.
  3. Update the current stretch.
  4. Evaluate triggers in a fixed order against the effective config.
  5. Advance the state machines from `DISCUSSION.md`: SM-01 mood, SM-02 nags, SM-03 breaks, SM-04 focus.
  6. The bubble arbiter picks at most one bubble.
  7. Append events, recompute mood, update the pet face.
- **Not counted** (A-20): the launcher, system UI, and this app.

**Screen off and breaks**
- No timers run while the screen is off.
- On screen-on, the engine compares the off and on timestamps:
  - ≥ 5 min off = a break (D-27): resets stretches and completes a pending break reminder.
- Day reset is detected the same lazy way: the day key is recomputed every tick.

**Meeting the 60-second promise**
- A 30 s slow tick keeps every reaction within 60 s (US-08 NFR).
- Opening the summary panel forces an immediate tick, so its numbers are ≤ 1 min old (US-07).

**Trigger catalog** — the contract between code and the line pools in config (H-02)

| Trigger | Fires when | Bubble | Mood |
|---|---|---|---|
| `limit_reached` | A daily limit (category or total) is crossed while in a covered app | Nag line, red glow | −10, once per limit per day |
| `limit_repeat` | N min later, still in a covered app | Next escalation line | — |
| `stretch_reached` | A continuous stretch limit is crossed | Nag line, red glow | −10, once per stretch limit per day |
| `snoozed` | User taps Snooze on a nag | — | −5 |
| `break_due` | X min of continuous screen-on | Line + notification N-02 | — |
| `break_taken` | Screen off ≥ 5 min after a reminder | Line on return | +5 |
| `doomscroll_10min` | Every 10 min of Social & scrolling | — | −3 |
| `goal_met` | A positive goal is met. Limit goals are judged at the day reset and only shown in trends (CR-05) | Reward line | +10, once per goal per day |
| `focus_started` | Session starts | Line | — |
| `focus_drift` | An app that isn't allowed is in front | Line | — |
| `focus_checkin` | Every X min in check-in mode | Line | — |
| `focus_completed` | Timer done or Done tapped | Reward line | +15 |
| `focus_broken` | A "no switching" goal is broken | Line | — |
| `idle` | ~30 min of screen time with no other bubble (D-26) | Line | — |
| `pet_returned` | Mood rises above 0 after running away | Line | — |

- **During focus:** only `focus_*` bubbles show. Nags, break reminders and rewards are held and fire at the end if they still apply (D-25).
- **While hidden:** nags and break reminders become notifications (N-04, N-02). Other bubbles are skipped (D-24).
- **Lines:** a line is picked at random from the pool for (personality, trigger, mood band), never repeating the last 3. Placeholders such as `{app}`, `{minutes}` and `{limit}` are filled in.

---

## 5. The config document

**One JSON document holds everything the admin can tune** (H-04)
- **Its shape** is defined once in `shared/config.schema.json` (JSON Schema draft-07).
- **The built-in defaults** live in `shared/default-config.json` (D-43). That one file is:
  - copied into the app's assets at build time (the built-in layer), and
  - seeded as the first published config on the server.

**Layering on the phone** (F-09, US-21, US-22, D-45)
1. **Base document:** the last valid admin config if there is one, otherwise the built-in defaults. The admin document replaces the built-in one *as a whole*; there's no deep merge of the two.
2. **User values:** applied per setting, only where the setting is `userEditable`, and clamped into the admin's `min`–`max`.
3. **Narrowed ranges:** if the admin later narrows a range, stored user values are clamped on the next read. The user's stored value isn't overwritten, so widening the range again restores it.

**Every tunable setting has the same shape**

```json
{ "default": 60, "min": 15, "max": 240, "userEditable": true }
```

- A "locked" setting is simply `"userEditable": false`.
- A limit of `0` means "off". By default there's no total daily limit (A-14).

**Outline** (illustrative excerpt; the full schema is designed in M2's LLD)

```json
{
  "schemaVersion": 1,
  "configVersion": 7,
  "features": { "focus": true, "breaks": true, "idleLines": true, "trends": true, "stats": true },
  "day": { "resetHour": { "default": 0, "min": 0, "max": 6, "userEditable": true } },
  "tracking": { "fastTickSeconds": 5, "slowTickSeconds": 30, "doomscrollCategories": ["social"] },
  "limits": {
    "categories": { "social": { "dailyMinutes": { "default": 60, "min": 15, "max": 240, "userEditable": true } } },
    "totalDailyMinutes": { "default": 0, "min": 0, "max": 960, "userEditable": true },
    "stretch": { "minutes": { "default": 30, "min": 10, "max": 120, "userEditable": true }, "categories": ["social", "games"] }
  },
  "nags": { "repeatMinutes": { "default": 10, "min": 5, "max": 30, "userEditable": true },
            "snoozeMinutes": { "default": 10, "min": 5, "max": 30, "userEditable": true },
            "maxSnoozesPerDay": 3 },
  "breaks": { "afterMinutes": { "default": 45, "min": 20, "max": 120, "userEditable": true }, "breakMinutes": 5 },
  "bubbles": { "minGapSeconds": 60, "showSeconds": 8 },
  "idle": { "everyMinutes": 30 },
  "hide": { "minutes": 30 },
  "mood": {
    "start": 70,
    "weights": { "focus_completed": 15, "goal_met": 10, "break_taken": 5,
                 "doomscroll_10min": -3, "limit_exceeded": -10, "snoozed": -5 },
    "bands": [ { "id": "great", "from": 85, "to": 100 }, { "id": "ran_away", "from": 0, "to": 0 } ]
  },
  "pets": [ { "id": "cat", "name": "Cat", "faces": { "great": "😻", "happy": "😸", "ran_away": "🐾" } } ],
  "personalities": [ { "id": "cheerful", "name": "Cheerful coach", "previewLine": "Let's make today a good one!" } ],
  "lines": { "cheerful": { "limit_reached": { "any": ["That's {limit} of {category} today. Stretch break?"] } } },
  "categoryDefaults": { "com.instagram.android": "social" },
  "androidCategoryMap": { "game": "games", "social": "social", "video": "entertainment", "productivity": "productive" }
}
```

**Validation** — the same rules on all three sides (D-39)

| Rule | Example |
|---|---|
| Types and required fields | `default` is a number |
| Ranges | `min ≤ default ≤ max` |
| References exist | every personality in `lines` exists; every pet has a face for every mood band |
| Coverage | mood bands cover 0–100 with no gaps; every personality has at least an `any` line for every trigger in §4 |

Where each side checks:
- **Console:** schema in the browser.
- **Backend:** schema + cross-field rules on save.
- **Phone:** Kotlin validator.

Files in `shared/fixtures/valid/` and `shared/fixtures/invalid/` must be accepted or rejected the same way by the backend and the phone tests.

**Versions**
- `configVersion` is the published version number. It's also the HTTP ETag.
- `schemaVersion` is the shape. It only changes for breaking changes.
  - The app sends the schema version it was built with, and the server returns the latest published document for that schema version.
  - Additive changes don't bump it: the app ignores unknown keys, and new fields have defaults.

---

## 6. Data model (overview)

Full fields, indexes and ERDs are written in each milestone's LLD.

**Phone — Room** (kept ≥ 31 days, older rows deleted at day reset, A-13)

| Table | Holds | Key |
|---|---|---|
| `daily_app_usage` | Seconds per app per day, with the category at the time (US-04) | `(day, package, category)` |
| `events` | Engine event log: limit_exceeded, nag_shown, snoozed, break_due, break_taken, doomscroll_10min, goal_met, focus_*, idle_shown, ran_away… | `id`, index on `day` |
| `focus_sessions` | Goal, allowed apps, pet mode, start/end, outcome, switch count | `id` |
| `app_categories` | Category per package, and whether the user chose it | `package` |

**Phone — DataStore**
- Setup progress and age confirmation.
- Chosen pet and personality.
- User values for user-editable settings, keyed by setting path.
- Idle lines on/off.
- Pet position; `hiddenUntil`.
- Stats: opt-in flag, install ID, last day sent.
  - The install ID is a random UUID created on opt-in and deleted on opt-out.

**Phone — files**
- `config_cache.json`: the last good admin config + its ETag, written atomically.

**Server — PostgreSQL** (week 2)

| Table | Holds | Key |
|---|---|---|
| `config_versions` | Every config document with status `draft`, `published` or `superseded`, and its schema version | `id` = `configVersion`. At most one draft and one published row per schema version |
| `daily_stats` | One row per install per day: app version, Android version, metrics (JSON) | `(install_id, day)`, index on `day`. Rows older than 12 months deleted daily |

- **Admin accounts:** there's no admin table. One admin, credentials in environment variables (A-16, D-38).

---

## 7. API overview (week 2)

**Standard shapes**
- **Success:** the resource itself as JSON (no envelope), or `204 No Content`.
- **Errors:** always

```json
{ "error": { "code": "VALIDATION_FAILED", "message": "Config is invalid", "details": [ { "path": "/limits/stretch/minutes/default", "message": "must be ≤ max" } ] } }
```

- Codes: `VALIDATION_FAILED` (400) · `UNAUTHENTICATED` (401) · `NOT_FOUND` (404) · `CONFLICT` (409) · `RATE_LIMITED` (429) · `INTERNAL` (500).

**Endpoints** (request and response bodies are defined in the M10, M11 and M14 LLDs)

| Method | Path | Who | Purpose |
|---|---|---|---|
| GET | `/health` | Hosting platform | Process up + database reachable |
| GET | `/v1/config?schema=<n>` | Any app, no auth | Latest published config for that schema version. ETag → `304` when unchanged |
| POST | `/v1/stats` | Opted-in apps | One day's aggregates → `204`. Upsert by `(installId, day)`. Rate-limited |
| POST | `/admin/login` | Anyone | → `204` + session cookie. 5 attempts/min/IP |
| POST | `/admin/logout` | Admin | Clears the cookie |
| GET | `/admin/config/published` | Admin | Current live document (to compare with the draft, W-02) |
| GET, PUT | `/admin/config/draft` | Admin | Read / save the draft. Schema + cross-field validation on save |
| POST | `/admin/config/publish` | Admin | Draft becomes the new published version |
| GET | `/admin/stats?from=&to=` | Admin | Aggregated metrics for the success metrics |
| GET | `/console/*` | Admin's browser | Built console files |

Version history + rollback (F-16, Could) reuses `config_versions`; it isn't in v1's milestones.

---

## 8. Data flows

### DF-1 Usage tick → nag (the core loop)

```mermaid
sequenceDiagram
  autonumber
  participant OS as Android usage events
  participant TRK as Tracker
  participant DB as Room
  participant RUL as Rules engine
  participant BUB as Bubble arbiter
  participant OVL as Overlay or Notifier
  participant MOOD as Mood
  Note over TRK: Every 5–30 s while the screen is on
  TRK->>OS: events since last tick
  OS-->>TRK: apps resumed and paused, screen on and off
  TRK->>DB: add seconds to day, app, category
  TRK->>RUL: front app, today's totals, current stretch
  RUL->>RUL: check triggers against the effective config
  alt Social limit crossed while in a Social app
    RUL->>DB: event limit_exceeded, once per limit per day
    RUL->>BUB: nag request
    BUB->>BUB: one bubble at a time, priority, 60 s gap, focus hold
    alt pet visible
      BUB->>OVL: red glow + speech bubble with Snooze
    else pet hidden
      BUB->>OVL: nag notification N-04
    end
  end
  RUL->>MOOD: today's events changed
  MOOD->>DB: read today's events
  MOOD-->>OVL: mood band → pet face
```

### DF-2 Tap the pet → summary panel

```mermaid
sequenceDiagram
  actor U as User
  participant OVL as Pet overlay
  participant TRK as Tracker
  participant REPO as Summary repository
  participant DB as Room
  U->>OVL: tap pet
  OVL->>TRK: tick now, so numbers are at most 1 min old
  TRK->>DB: credit time since the last tick
  OVL->>REPO: today's summary
  REPO->>DB: totals by category, goal progress
  DB-->>REPO: rows
  REPO-->>OVL: total, productive vs doomscrolling, goals
  OVL-->>U: panel opens within 1 s
```

### DF-3 Focus session

```mermaid
sequenceDiagram
  actor U as User
  participant SCR as Focus setup screen
  participant DB as Room
  participant ENG as PetService engine
  participant OVL as Pet overlay
  U->>SCR: goal, allowed apps, pet mode, Start
  SCR->>DB: insert focus session, status running
  DB-->>ENG: active session observed
  ENG->>OVL: timer badge + focus_started line
  Note over ENG: Nags and break reminders are held (D-25)
  loop every tick
    ENG->>ENG: is the front app allowed?
    alt not allowed
      ENG->>DB: record the switch
      ENG->>OVL: focus_drift line
    end
  end
  ENG->>DB: session completed + event focus_completed
  ENG->>OVL: reward line, mood up
  ENG->>ENG: release held nags and reminders that still apply
```

### DF-4 Admin publishes → phones apply the new config

```mermaid
sequenceDiagram
  actor A as Admin
  participant CON as Console in browser
  participant API as Fastify API
  participant PG as PostgreSQL
  participant APP as Phone app
  A->>CON: edit values in the generated form
  CON->>CON: validate against the schema
  CON->>API: PUT /admin/config/draft
  API->>API: schema + cross-field rules
  API->>PG: save draft
  A->>CON: Publish
  CON->>API: POST /admin/config/publish
  API->>PG: insert published version n, supersede n-1
  API-->>CON: version n is live
  Note over APP: On start and every 6 h (A-17)
  APP->>API: GET /v1/config, If-None-Match n-1
  API-->>APP: 200, document, ETag n
  APP->>APP: parse + Kotlin validator
  alt valid
    APP->>APP: save cache atomically, recompute effective config
  else invalid or offline
    APP->>APP: keep last good, else built-in
  end
```

### DF-5 Opt-in daily stats

```mermaid
sequenceDiagram
  participant ENG as PetService
  participant DB as Room
  participant API as Fastify API
  participant PG as PostgreSQL
  actor A as Admin
  Note over ENG: After the day reset, only if the user opted in
  ENG->>DB: aggregate unsent days, at most 7 back
  ENG->>API: POST /v1/stats with installId, day, metrics
  API->>API: schema check, rate limit per install and IP
  API->>PG: upsert install_id + day
  API-->>ENG: 204
  ENG->>DB: remember the last day sent
  A->>API: GET /admin/stats for a date range
  API->>PG: aggregate by day
  API-->>A: success metrics over time
```

---

## 9. Cross-cutting concerns

**Time and days**
- All day logic goes through one `DayClock`. It uses `java.time` with the device time zone and an injectable clock for tests.
- The **day key** is the local date shifted by the reset hour. With reset 04:00, 03:30 belongs to the previous day.
- Seconds are credited to the day key at the moment they happen. A time-zone change never double-counts (NFR).
- **Day reset:** new totals, mood back to start, nags back to `Under`, snooze count 0, stretches reset.
  - A running focus session continues and belongs to the day it started (A-25).

**Config:** see §5. A bad config never crashes the app (US-21). Invalid documents are ignored and logged.

**Errors**
- **App:** no silent catches. Failures in the engine are logged and the tick continues. Network failures retry at the next 6-hour check.
- **Backend:** one central error handler maps errors to the standard shape (§7). Unknown errors return `INTERNAL` without internal details.

**Logging**
- **App:**
  - Release builds never log which apps the user opens.
  - Debug builds log engine decisions for testing.
  - Crash data comes from Play Console's Android vitals (no crash SDK).
- **Backend:**
  - Fastify's logger writes JSON to stdout with request IDs.
  - Request bodies, passwords, cookies and install IDs are never logged.

**Security** (backend)
- **Admin session:** a signed, `httpOnly`, `Secure`, `SameSite=Strict` cookie, valid 12 h. Rotating `SESSION_SECRET` logs out everywhere.
- **CSRF:**
  - The `SameSite=Strict` cookie.
  - Admin routes accept only `application/json`.
  - No CORS is enabled: the console is served from the same address.
- **Headers:** security headers including a Content Security Policy for the console (helmet).
- **Rate limits** (all behind the host's proxy, with `TRUST_PROXY` on so limits see real client IPs):
  - Login: 5/min/IP.
  - Stats: per install ID and per IP.
- **Database:** queries are always parameterised; the app's DB user has access to its own tables only.
- **HTTPS:** provided by the hosting platform. The release app refuses plain HTTP.

**Security** (app)
- No secrets in the app (A-10).
- Debug builds may talk to the dev backend over plain HTTP on the emulator's host address only.

**Privacy — what ever leaves the phone**
- `GET /v1/config`: no identifiers.
- `POST /v1/stats`: only if opted in; daily aggregates only (A-09).
- Nothing else. The privacy policy and Play's Data safety form must say exactly this (M15).

**Battery** (R-07, A-19)
- No ticks while the screen is off.
- No wake locks, no exact alarms.
- Adaptive tick interval.
- Measured on a real phone in M9.

**UI rules** (target SDK 36)
- Edge-to-edge is always on, so screens handle system bar insets.
- The predictive back gesture is handled by Navigation 3.
- Alerts never rely on colour alone (glow + bubble + optional notification).
- Pet touch target ≥ 48 dp.
- TalkBack and large text work on setup, settings, home and trends.

---

## 10. Android permissions and Play policy

| Permission / declaration | How it's granted | Why | Without it |
|---|---|---|---|
| `PACKAGE_USAGE_STATS` | User, in Settings → Usage access | Know which app is in front and for how long | No tracking; the pet can't react |
| `SYSTEM_ALERT_WINDOW` | User, in Settings → Display over other apps | Draw the pet | The pet can't appear |
| `POST_NOTIFICATIONS` | Runtime prompt (Android 13+) | Persistent notification, break reminders, nags while hidden, permission lost | No reminders outside the overlay |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | Automatic | The always-on service | — |
| `RECEIVE_BOOT_COMPLETED` | Automatic | Bring the pet back after a reboot | Pet returns only when the app is opened |
| `INTERNET` | Automatic | Config fetch, opt-in stats (week 2) | Built-in config only |
| Battery optimisation exemption | User, on the battery settings screen we open | Lower the chance the system stops the service (R-04) | Some brands kill the pet |
| `<queries>` for launcher apps | Manifest declaration, no permission | List the user's apps for categorisation | — |

**Play policy notes**
- **Special-use foreground service:**
  - The manifest describes the use case.
  - The Play Console declaration needs a text justification and a demo video (R-03, M15).
- **Battery exemption:** we open the battery settings list (`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`, no permission needed). The one-tap request dialog is reserved for Google's acceptable use cases, which don't clearly include this app.
- **Package visibility:** we declare a `<queries>` entry for apps with a launcher icon instead of the restricted `QUERY_ALL_PACKAGES` permission. The exact behaviour is verified in M3 (R-10).
- **Restart after reboot:** confirmed allowed. Android 15 blocks `BOOT_COMPLETED` from starting only `dataSync`, `camera`, `mediaPlayback`, `phoneCall`, `mediaProjection` and `microphone` services, not `specialUse`.
- **Other restart paths are verified in M4 (R-09):** restart after an app update, and restart after the system kills the process.
  - The fallback is a notification that brings the pet back on tap.
  - Background: since Android 15, holding the overlay permission alone no longer lets an app start a foreground service from the background; a visible overlay window is also needed.
- **Health apps declaration:** "no health features" (D-02).
- **Target audience:** 18+.

---

## 11. Permission matrix (role × action)

| Action | Phone user | Admin (logged in) | Anyone on the internet |
|---|---|---|---|
| Use the app, change user-editable settings within range | ✅ | — | — |
| Change a locked setting or a range | ❌ | ✅ via published config | ❌ |
| `GET /v1/config` | ✅ (the app) | ✅ | ✅ (public, no secrets) |
| `POST /v1/stats` | ✅ only if opted in | — | ✅ but schema-checked, rate-limited, anonymous |
| `POST /admin/login` | — | ✅ | ✅ rate-limited |
| Read / edit the draft, publish | ❌ | ✅ | ❌ (`401`) |
| Read aggregated stats | ❌ | ✅ | ❌ (`401`) |
| See any one person's usage | ❌ — it never leaves their phone | ❌ | ❌ |

Tests: every admin route is checked for `401` without a session (M11).

---

## 12. Config and environments

**Backend environment variables** (mirrored in `backend/.env.example`)

| Variable | Purpose | Example (dev) |
|---|---|---|
| `NODE_ENV` | `development` or `production` | `development` |
| `PORT` | HTTP port | `3000` |
| `DATABASE_URL` | Postgres connection string | `postgres://pet:pet@db:5432/pet` |
| `ADMIN_USERNAME` | Admin login name | `admin` |
| `ADMIN_PASSWORD_HASH` | scrypt hash, generated by `npm run hash-password` | — |
| `SESSION_SECRET` | ≥ 32 random bytes; signs the admin cookie | — |
| `TRUST_PROXY` | `true` behind the host's proxy, so rate limits see real client IPs | `false` |
| `LOG_LEVEL` | Logger level | `info` |

**Android build settings**
- `CONFIG_BASE_URL` per build type:
  - debug → the dev backend (`http://10.0.2.2:3000` from the emulator).
  - release → the production HTTPS address (set in M15).
- `applicationId`: `com.companionstudio.nyx`; app name: Nyx (D-49).

**Environments** (A-27)
- **dev:** laptop running Docker Compose (API + Postgres), the console dev server, and the emulator or a USB-connected phone.
- **prod:** the hosting platform.
- **No staging environment in v1.**
  - Config changes are protected by draft → publish and validation on all three sides.
  - Code is tested locally with the same Docker image that ships.

**Docker topology**
- **Local — `docker-compose.yml`:**
  - `db`: `postgres:18-alpine`, port 5432, volume `pgdata`.
  - `api`: built from `backend/Dockerfile`, port 3000, env from `backend/.env`, starts after `db` is healthy.
  - The console runs separately with `npm run dev` (Vite, port 5173) and proxies `/admin` and `/v1` to port 3000.
- **Production image — `backend/Dockerfile`, multi-stage, build context = repo root** (D-42):
  1. Build the console (`admin/`) into static files.
  2. Copy the backend and the console files into one Node image; the backend serves them at `/console/`.
  - `admin/` therefore has no Dockerfile of its own: it's static files, not a server. This is a deliberate exception to AGENTS.md §4's "own Dockerfile" rule.

---

## 13. Repo map — where to go to change something

| I want to… | Go to |
|---|---|
| Change a number, line, pet, personality or switch for everyone | The admin console → Publish. No code. |
| Add a new setting | `shared/config.schema.json` + `shared/default-config.json` + `android/…/config/` (model, validator) + a fixture in `shared/fixtures/` + the feature package that uses it |
| Change the defaults the app ships with | `shared/default-config.json` |
| Change how usage is counted, categories, stretches | `android/…/tracking/` |
| Change when the pet nags, reminds or talks | `android/…/rules/` |
| Change mood math | `android/…/mood/` (weights and bands are config) |
| Change the pet, bubble or panel | `android/…/pet/` |
| Change focus sessions | `android/…/focus/` |
| Add or change an app screen | `android/…/<feature>/` + `AppNavigation` |
| Change notifications, boot or restart behaviour | `android/…/service/` |
| Change day boundaries or time-zone handling | `android/…/data/DayClock` |
| Add an API endpoint | `backend/src/features/<feature>/<feature>.routes.js` |
| Change admin login or session | `backend/src/features/auth/` + `backend/src/middleware/` |
| Change the database schema | a new `backend/migrations/NNN_<name>.sql` |
| Change a console screen | `admin/src/features/<feature>/` |
| Change seed or test data | `shared/fixtures/` · `backend/seeds/` · Android debug scenarios in `android/app/src/debug/` |

`android/…` = `android/app/src/main/java/com/companionstudio/nyx/`. The depth is Android's convention; everything below it is one level of feature packages.

**Backend layout**

```text
backend/
├── src/
│   ├── features/
│   │   ├── auth/      ← auth.routes.js, auth.service.js, auth.test.js
│   │   ├── config/    ← config.routes.js, config.service.js, config.model.js, config.validation.js, config.test.js
│   │   ├── stats/     ← stats.routes.js, stats.service.js, stats.model.js, stats.test.js
│   │   └── health/
│   ├── middleware/    ← requireAdmin.js, errorHandler.js
│   ├── lib/           ← db.js, password.js (scrypt), schema.js (loads shared schema)
│   ├── app.js         ← builds the Fastify app (used by tests)
│   └── server.js      ← starts it
├── migrations/        ← 001_init.sql …
├── scripts/           ← migrate.js, hash-password.js
├── seeds/
├── Dockerfile
├── .env.example
└── README.md
```

**Console layout**

```text
admin/
├── src/
│   ├── features/
│   │   ├── login/
│   │   ├── config/    ← W-02 config editor, W-03 pets & lines
│   │   └── stats/     ← W-04
│   ├── api.js
│   ├── App.jsx
│   └── main.jsx
├── index.html
├── vite.config.js
└── README.md
```

---

## 14. Low-level design (per milestone)

Written at the start of each milestone, before code.

| Module | Milestone | LLD status |
|---|---|---|
| Project setup | M0 | not needed |
| App shell, storage, DayClock | M1 | pending |
| Config: schema, defaults, validator, layering | M2 | pending |
| Setup flow + app categories | M3 | pending |
| PetService + usage tracking | M4 | pending |
| Overlay: pet, bubble, panel, hide | M5 | pending |
| Lines, bubble arbiter, mood | M6 | pending |
| Limits, nags, breaks, idle lines | M7 | pending |
| Focus sessions | M8 | pending |
| Backend foundation + config API | M10 | pending |
| Admin auth + console | M11 | pending |
| App remote config | M12 | pending |
| Trends + positive goals | M13 | pending |
| Opt-in stats | M14 | pending |
