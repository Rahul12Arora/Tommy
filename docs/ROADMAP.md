# Roadmap — Nyx · v1.0 (approved 2026-10-18)

> Phase 4 output. One milestone at a time; each is a vertical slice that can be verified on its own.
> Every milestone starts by writing its LLD in `ARCHITECTURE.md` §14, before code.
> Status values: `pending` · `in-progress` · `done`. Tick tasks as they finish.
> Last updated: 2026-10-18

---

## 1. Timeline

**Week 1 — working build on your phone and the emulator by Sun 25 Oct (D-23)**

| Day | Milestones |
|---|---|
| Sun 18 Oct | Phase 4 approval → M0 Foundation |
| Mon 19 Oct | M1 Shell & storage · M2 Config |
| Tue 20 Oct | M3 Setup flow |
| Wed 21 Oct | M4 Service & tracking |
| Thu 22 Oct | M5 Pet overlay · M6 Lines, bubbles, mood |
| Fri 23 Oct | M7 Limits, nags, breaks, idle lines |
| Sat 24 Oct | M8 Focus sessions |
| Sun 25 Oct | M9 Week-1 check on a real phone → tag `week1-build` |

**Week 2 — backend, console, trends, stats, release**

| Day | Milestones |
|---|---|
| Mon 26 Oct | M10 Backend + config API |
| Tue 27 Oct | M11 Admin login + console |
| Wed 28 Oct | M12 App fetches remote config |
| Thu 29 Oct | M13 Trends + positive goals |
| Fri 30 Oct | M14 Opt-in stats |
| Sat 31 Oct → | M15 Deploy + Play submission. Public listing after Google's review (usually a few days, R-08) |

**Cut line if week 1 runs late** (approved, D-46)
- If M7 isn't done by Fri 23 Oct evening, these move to week 2, in this order:
  1. Idle lines (F-20)
  2. Focus check-in mode and the "no switching" goal (fixed-time and until-done stay)
  3. Brand-specific battery steps beyond Xiaomi, Samsung and a generic guide
  4. Hide pet (F-19)
- Nothing else moves. The core loop — track → limit → nag → mood → focus — is the week-1 promise.

---

## 2. Test strategy

**Layers**

| Layer | What | Tool |
|---|---|---|
| Unit (Android, JVM) | Rules, nag/break/focus state machines, bubble arbiter, mood, config validator + layering, `DayClock`, usage-event → time crediting | JUnit 4 + coroutines-test with a fake clock |
| Device (Android) | Room DAOs; Compose UI tests for setup gating and Home | AndroidX Test, Compose UI test |
| Contract | `shared/fixtures/valid` and `invalid` accepted or rejected identically by the phone and the backend | JUnit (phone) · `node:test` (backend) |
| API (backend) | Every endpoint: success, validation error, unauthenticated, rate-limited | `node:test` + Fastify request injection; Postgres from Docker Compose |
| Manual QA | A short checklist per milestone, on the emulator; real phone from M4 on | Emulator + your phone |

**Rules** (AGENTS.md Track B)
- Test names carry story IDs, e.g. `US08_limitReached_glowsAndShowsBubbleWithin60s`.
- Bug fix = failing test first.
- No test is skipped or weakened to go green.

**Coverage target**
- ≥ 90% line coverage on `rules/`, `mood/`, `focus/` session logic, `config/` validator + layering, and `DayClock`. Measured with AGP's built-in unit-test coverage (JaCoCo): `./gradlew createDebugUnitTestCoverageReport` → `android/app/build/reports/coverage/test/debug/index.html`.
- ≥ 80% on backend `features/`, measured with Node's built-in test coverage.
- Screens aren't held to a number.

**Testing time-based behaviour without waiting** (Track A)
- **Debug-only fake usage source** (M4): scripted scenarios such as "65 min Instagram" or "screen off 6 min" replace real usage events.
- **Debug fast-clock switch:** time-based features can be checked in seconds on the emulator.
- Scenarios are files in `android/app/src/debug/`, deterministic and re-runnable.

**CI** (GitHub Actions, from M0)
- Android: build + unit tests + lint.
- Node (from M10): lint + tests + console build.
- Red CI blocks a milestone from being marked done.

**Before release** (M15)
- `npm audit` and a review of the Android dependency list.
- A simple load check of `/v1/config` and `/v1/stats` against the scale NFR, using a small Node script with built-in `fetch`.
- Accessibility pass: TalkBack + large text.
- Devices: emulator on API 30, 35 and 37, plus your phone.

---

## 3. Out of scope for v1

See `REQUIREMENTS.md` §8:
- iOS
- Chat with the pet
- Blocking apps
- Detecting content inside apps
- Accounts, login, cloud backup
- ADHD or health claims
- Languages other than English
- Custom pet art
- Users under 18

Also not in v1's milestones:
- F-16 config history + rollback (Could)
- A staging environment (A-27)

---

## 4. Milestones — week 1

### M0 — Foundation            status: in-progress
Goal: a repo where the Android app builds, runs a blank screen on the emulator, and CI is green.
Depends on: Phase 4 approval (D-46) · app ID (D-49) · GitHub repo (D-48)
Stories: all (foundation)
Tasks:
- [x] Repo `github.com/Rahul12Arora/Tommy`: `.gitignore` (Android, Node, `.env`, keystores, `local.properties`); `README.md`; `AGENTS.md` + `docs/` (branch `feat/m0-foundation`)
- [x] `android/` project (Android Studio "Empty Activity" wizard, then aligned): app name Nyx, app ID `com.companionstudio.nyx`, AGP 9.4.1 (CR-06), Gradle 9.6.0, Kotlin 2.4.21, Compose BOM 2026.09.00, min/target/compile 30/36/37, version catalog with exactly the TECH_STACK register
- [x] Confirm KSP 2.3.12 builds with Kotlin 2.4.21 (TECH_STACK §8); if not, record the fallback as a decision
- [x] `shared/` with `README.md` and empty `fixtures/valid` + `fixtures/invalid` (filled in M2)
- [x] Android Lint configured; unit-test coverage turned on
- [ ] GitHub Actions: Android build + unit tests + lint
- [ ] `android/README.md`, `shared/README.md`
- [x] AGENTS.md Project card: name, commands; `docs/PROGRESS.md` created
Done when:
- [ ] `./gradlew assembleDebug test lint` passes locally and in CI
- [ ] The app opens a placeholder screen on the emulator
Verify with: `./gradlew assembleDebug test lint` · CI run on GitHub · emulator launch
Not in this milestone: `backend/`, `admin/` (→ M10, M11)

### M1 — Shell & storage            status: pending
Goal: the app has navigation, theme, `AppContainer`, the Room database, DataStore and `DayClock`; Home shows fake numbers through a ViewModel.
Depends on: M0
Stories: US-28 (screen shell)
Tasks:
- [ ] LLD in ARCHITECTURE.md §14
- [ ] `PetApp`, `AppContainer`, `MainActivity`, Navigation 3 with predictive back, edge-to-edge theme
- [ ] Room `AppDatabase` (empty schema + migration policy); DataStore
- [ ] `DayClock`: day key with reset hour, time-zone change, injectable clock
- [ ] Home (S-08) ↔ `HomeViewModel` ↔ fake summary repository
- [ ] Tests: `DayClock` (reset hour, midnight, time-zone change, DST-free zones); Home UI test
Done when:
- [ ] Home renders fake totals; back gesture works; DayClock tests pass
Verify with: `./gradlew test connectedAndroidTest` · emulator
Not in this milestone: real data (→ M4), setup gating (→ M3)

### M2 — Config            status: pending
Goal: the app loads, validates and layers config; built-in defaults are one shared file.
Depends on: M1
Stories: US-21 (built-in fallback), US-22 (user values within ranges), US-05 (data side)
Tasks:
- [ ] LLD: full schema, Kotlin models, validator rules, layering, how user values are keyed
- [ ] `shared/config.schema.json` v1 and `shared/default-config.json` (placeholder numbers from A-14, A-24; at least 2 personalities with lines for every trigger; Cat and Blob faces per band)
- [ ] `shared/fixtures/valid/` and `invalid/` (one file per validation rule)
- [ ] Gradle copies `shared/default-config.json` into app assets
- [ ] Kotlin models (kotlinx.serialization, unknown keys ignored), validator, `ConfigManager` (base doc + user values, clamping, locked settings)
- [ ] Tests: every fixture; clamping when a range narrows; locked settings ignore user values; invalid cache falls back to built-in
Done when:
- [ ] The app starts on built-in config; all fixture tests pass; ≥ 90% coverage on `config/`
Verify with: `./gradlew test`
Not in this milestone: fetching from a server (→ M12)

### M3 — Setup flow            status: pending
Goal: a fresh install walks through S-01 to S-07 and lands on Home.
Depends on: M2
Stories: US-01, US-02, US-03, US-04, US-05, US-24 (the choice only)
Tasks:
- [ ] LLD: screens, state, permission checks, brand detection, `<queries>` behaviour (R-10), Android category mapping
- [ ] S-01 Welcome, S-02 Age check (under 18 stops)
- [ ] S-03 Permissions: usage access, overlay, notifications, battery settings; auto-tick on return; "what won't work" summary for skipped steps; brand-specific steps (Xiaomi, Samsung, Vivo, Oppo, generic)
- [ ] S-04 Pet + personality picker with preview lines
- [ ] S-05 App categories: launcher apps listed, suggested by admin default → Android category → Neutral; search; save to `app_categories`
- [ ] S-06 Limits & goals within ranges; locked items read-only
- [ ] S-07 Stats opt-in (stored only, default off)
- [ ] Launcher opens setup until done, then Home
- [ ] Tests: setup gating, under-18 stop, category suggestion order, range-bound inputs
Done when:
- [ ] Fresh install → all steps → Home, on the emulator and your phone; skipping a permission shows what won't work
Verify with: `./gradlew test connectedAndroidTest` · manual QA checklist
Not in this milestone: the pet appearing (→ M5)

### M4 — Service & tracking            status: pending
Goal: an always-on service tracks real usage into per-app/category daily totals and survives reboots.
Depends on: M3
Stories: US-06 (restart after reboot), US-26, US-28 (real numbers), F-02
Tasks:
- [ ] LLD: service lifecycle, restart paths (boot, app update, process death — R-09), tick loop, event → seconds crediting, stretches, exclusions (A-20), screen on/off
- [ ] `PetService`: special-use foreground service + persistent notification N-01; started after setup and from boot
- [ ] Tracker: adaptive tick, foreground app, credit `(day, package, category)`, stretch counter, new-app category suggestion
- [ ] Day reset; 31-day cleanup
- [ ] Permission-lost detection → N-03 → opens S-03 (US-26)
- [ ] Debug fake usage source + fast clock + scenario files
- [ ] Home shows real totals
- [ ] Tests: crediting across app switches, screen off, day reset, category change mid-day, exclusions
Done when:
- [ ] On your phone: an hour of normal use shows matching totals on Home (± 2 min vs Android's Digital Wellbeing); the pet service is back after a reboot
Verify with: unit tests · fake scenario "65 min Instagram" · reboot test on your phone
Not in this milestone: the pet on screen (→ M5)

### M5 — Pet overlay            status: pending
Goal: the emoji pet floats over every app, can be dragged and hidden, and opens a live summary panel.
Depends on: M4
Stories: US-06, US-07, US-29 (hide + show), F-15
Tasks:
- [ ] LLD: overlay windows (pet, bubble, panel), touch handling, Compose inside a service, emoji2 setup, positions, hide timer
- [ ] Pet window sized to the pet only (taps elsewhere reach the app below); drag + snap to edge; position remembered
- [ ] Speech bubble component (used from M6)
- [ ] Summary panel O-03: total, productive vs doomscrolling, goal progress; forces a tick; Start focus, Open app, Hide
- [ ] Hide for the configured minutes; "Show pet" on N-01 and Home
- [ ] emoji2 with "replace all"
- [ ] Tests: panel numbers match Home; hide/show timing (fake clock)
Done when:
- [ ] Pet over YouTube/Instagram/home screen; panel opens ≤ 1 s; hide returns after the set time
Verify with: emulator + phone · manual QA checklist
Not in this milestone: what the pet says (→ M6)

### M6 — Lines, bubbles, mood            status: pending
Goal: the pet has a personality and a mood that its face shows.
Depends on: M5
Stories: US-15, US-16, US-03 (lines in use)
Tasks:
- [ ] LLD: event log schema, mood calculation, bands → faces, ran-away/return, bubble arbiter (priority, gap, focus hold, hidden), line picker (no repeat of last 3, placeholders)
- [ ] Event log; mood recomputed from today's events; day reset
- [ ] Bubble arbiter + line picker
- [ ] Ran away (🐾 / 💨) and `pet_returned`
- [ ] `doomscroll_10min` events from tracking
- [ ] Tests: mood math for every weight, clamping 0–100, ran away at 0, return above 0, arbiter priority and gap
Done when:
- [ ] Fake scenario "2 h Instagram" walks the cat's face down the bands to 🐾; a break brings it back
Verify with: `./gradlew test` · fake scenarios on the emulator
Not in this milestone: limits and breaks triggering lines (→ M7)

### M7 — Limits, nags, breaks, idle lines            status: pending
Goal: the pet nags when limits are crossed, reminds breaks, and talks now and then.
Depends on: M6
Stories: US-08, US-09, US-10, US-11, US-17 (limit goals), US-27, US-29 (notifications while hidden)
Tasks:
- [ ] LLD: SM-02 per limit, SM-03, escalation lines, snooze limits, limit goals judged at day reset with no mood effect (CR-05), idle timer, hidden → notifications
- [ ] Daily category + total limits → `limit_reached`, red glow, bubble, repeat every N min, snooze (only that nag), max snoozes/day
- [ ] Stretch limits; reset only by a 5-min break
- [ ] Break reminders: bubble + N-02; `break_taken` after ≥ 5 min off
- [ ] Idle lines (about every 30 min of screen time; off switch in settings and config)
- [ ] While hidden: nags → N-04, break reminders → N-02
- [ ] Settings screen S-11 for nag interval, snooze, break trigger, idle lines, day reset hour
- [ ] Tests: every SM-02 and SM-03 transition, snooze isolation, max snoozes, stretch reset, held while hidden
Done when:
- [ ] Fake "65 min Instagram": glow + bubble within 60 s, repeats, snooze works; fake "50 min screen-on": break reminder bubble + notification
Verify with: `./gradlew test` · fake scenarios · manual QA checklist
Not in this milestone: positive goals (→ M13)

### M8 — Focus sessions            status: pending
Goal: the user can start a focus session and the pet keeps them on task.
Depends on: M7
Stories: US-12, US-13, US-14
Tasks:
- [ ] LLD: session model, goal types, allowed-app picker, drift detection, check-ins, rewards, held nags release (D-25)
- [ ] S-09 Focus setup (from the panel and Home)
- [ ] SM-04: running, drifting, paused on lock, completed, broken, ended early
- [ ] Pet modes: quiet / check-in every X min / reward at the end
- [ ] Hold nags, break reminders and idle lines during focus; release those that still apply
- [ ] Tests: every SM-04 transition, A-02 exclusions (shade, panel, call), held-nag release, day reset during a session (A-25)
Done when:
- [ ] A 25-min "no switching" session on YouTube: opening Instagram breaks it; a fixed-time session completes with a reward and mood +15
Verify with: `./gradlew test` · fake scenarios · real session on your phone
Not in this milestone: focus stats in trends (→ M13)

### M9 — Week-1 check            status: pending
Goal: the week-1 build works on your real phone for a full day.
Depends on: M8
Stories: NFRs (performance, battery, accessibility) for the week-1 features
Tasks:
- [ ] Full day on your phone: pet stays alive (R-04), reactions ≤ 60 s, panel ≤ 1 s
- [ ] Battery: app's own use from Android's battery screen vs the 3% budget (A-19); tune ticks if over
- [ ] Empty, loading and error states on Home, setup and settings
- [ ] TalkBack + large text on setup, Home, settings
- [ ] Fix bugs found (each with a failing test first)
- [ ] Tag `week1-build`
Done when:
- [ ] The QA checklist for M3–M8 passes on your phone; battery is within budget or the gap is logged
Verify with: manual QA checklist · Android battery usage screen

---

## 5. Milestones — week 2

### M10 — Backend + config API            status: pending
Goal: the backend serves the published config with ETags, locally in Docker.
Depends on: M2 (schema + default config)
Stories: US-19 (serving), US-21
Tasks:
- [ ] LLD: tables, migrations, config endpoints, validation, error handler, logging, health
- [ ] `backend/` scaffold per ARCHITECTURE §13; Biome; `.env.example`; `README.md`
- [ ] `docker-compose.yml`: Postgres 18 + API
- [ ] Migration runner + `001_init.sql` (`config_versions`, `daily_stats`)
- [ ] Seed: publish `shared/default-config.json` as version 1
- [ ] `GET /v1/config?schema=` with ETag / 304; `GET /health`
- [ ] Shared fixtures test (same results as the phone)
- [ ] CI: Node lint + tests
Done when:
- [ ] `curl` returns the config, then `304` with the ETag; fixture tests match the phone's
Verify with: `npm test` · `curl -i localhost:3000/v1/config?schema=1`
Not in this milestone: admin routes (→ M11)

### M11 — Admin login + console            status: pending
Goal: you can log in, edit the draft in a generated form and publish.
Depends on: M10
Stories: US-19, US-20, US-23
Tasks:
- [ ] LLD: login, session cookie, CSRF rules, draft/publish endpoints, console screens W-01 to W-03
- [ ] `hash-password` script; login/logout; `requireAdmin`; rate limit
- [ ] Draft/published/publish endpoints with cross-field validation
- [ ] `admin/`: Vite + React; login; config editor (generated form, draft vs published); pets & lines
- [ ] Production image builds the console into `/console/` (D-42)
- [ ] Tests: every admin route `401` without a session; login rate limit; invalid draft rejected with details
Done when:
- [ ] Change the Social limit default in the console → publish → `GET /v1/config` shows it with a new ETag
Verify with: `npm test` · browser walkthrough
Not in this milestone: stats view (→ M14)

### M12 — App fetches remote config            status: pending
Goal: phones pick up published config within 6 hours, safely.
Depends on: M11
Stories: US-19 (pickup), US-21, US-22
Tasks:
- [ ] LLD: `ConfigClient`, schedule, cache, error handling, debug base URL for a USB phone
- [ ] Fetch on service start and every 6 h; ETag; atomic cache; fallback chain
- [ ] Tests: 200 / 304 / offline / invalid document / unknown schema version
Done when:
- [ ] Publish a new limit → the app uses it after restart; an invalid published fixture (forced in dev) is ignored
Verify with: `./gradlew test` · emulator against local Docker

### M13 — Trends + positive goals            status: pending
Goal: 7- and 30-day trends and positive goals.
Depends on: M9
Stories: US-17 (positive goals), US-18
Tasks:
- [ ] LLD: queries, chart drawing (plain Compose Canvas), positive goal types
- [ ] S-10 Trends: daily time per category, goals met per day (limit goals kept/missed, CR-05), empty days before install
- [ ] Positive goals: focus sessions per day, productive minutes per day → `goal_met`
- [ ] Tests: aggregation queries, empty-day handling, goal met once per day
Done when:
- [ ] 30 days of seeded debug data show correctly; a positive goal raises mood once
Verify with: `./gradlew test` · debug seed

### M14 — Opt-in stats            status: pending
Goal: opted-in phones send daily aggregates; you see the success metrics.
Depends on: M12
Stories: US-24, US-25
Tasks:
- [ ] LLD: payload schema, aggregation queries, rate limits, retention job, W-04
- [ ] App: aggregate + upload after day reset (max 7 days back); stop immediately on opt-out; delete install ID
- [ ] `POST /v1/stats`; `GET /admin/stats`; 12-month retention
- [ ] Console W-04: the REQUIREMENTS §2 metrics over time (simple SVG)
- [ ] Seed: 50 installs × 30 days, fixed random seed; volume check with 10k installs
- [ ] Tests: schema rejects extra fields; rate limit; upsert; opt-out stops sending
Done when:
- [ ] Opted-in emulator's day appears in W-04; opting out stops uploads
Verify with: `npm test` · `./gradlew test` · console

### M15 — Deploy + Play submission            status: pending
Goal: backend live over HTTPS; the app submitted to Google Play.
Depends on: M14
Stories: US-23 (HTTPS), all NFRs
Tasks:
- [ ] Choose the host; managed Postgres with backups; deploy the image; secrets set; `TRUST_PROXY=true`
- [ ] Restore test from a backup
- [ ] Release app: signing key outside the repo, R8 shrinking, version 1.0.0, production `CONFIG_BASE_URL`
- [ ] Privacy policy (what leaves the phone, ARCHITECTURE §9); Data safety form
- [ ] Play declarations: special-use foreground service text + demo video, health apps ("none"), target audience 18+, content rating
- [ ] Store listing, screenshots
- [ ] Release checks: `npm audit`, load check, accessibility, devices (API 30, 35, 37 + Pixel 7a)
- [ ] One day on a borrowed Xiaomi or Samsung phone: the pet survives with the brand's battery steps (R-04)
- [ ] Submit to production; tag `v1.0.0`
Done when:
- [ ] `/health` is green on production; the app is submitted and in review
Verify with: production `curl` · Play Console status

---

## 6. Story → milestone map

| Story | Milestone(s) |
|---|---|
| US-01, US-02, US-03, US-04, US-05 | M3 (US-03 lines in M6; US-04 new apps in M4; US-05 data in M2) |
| US-06 | M4 (restart), M5 (overlay) |
| US-07 | M5 |
| US-08, US-09, US-10, US-11 | M7 |
| US-12, US-13, US-14 | M8 |
| US-15, US-16 | M6 |
| US-17 | M7 (limit goals), M13 (positive goals) |
| US-18 | M13 |
| US-19 | M10, M11, M12 |
| US-20 | M11 |
| US-21, US-22 | M2, M12 |
| US-23 | M11, M15 |
| US-24 | M3 (choice), M14 |
| US-25 | M14 |
| US-26 | M4 |
| US-27 | M7 |
| US-28 | M1, M4 |
| US-29 | M5, M7 |
