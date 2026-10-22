# AGENTS.md — AI-Assisted Development Protocol

> Instruction set for AI coding agents (Claude Code, GitHub Copilot, Gemini in Android Studio, Cursor, etc.).
> Tool-agnostic. Lives at the repo root.
> The docs this protocol creates are the single source of truth — not chat history, not model memory.

---

## 0. Project card (keep current)

- **Project:** Nyx (`com.companionstudio.nyx`) — on-screen emoji pet for screen-time awareness, limits and focus · repo `github.com/Rahul12Arora/Tommy`
- **Platform:** Android app (Kotlin + Compose) · Node.js backend · web admin console (React)
- **Size tier:** standard — see §4 and `docs/TECH_STACK.md` §3
- **Current phase:** 5 · **Current milestone:** M8 Focus sessions (next; M7 done 2026-10-22)
- **Collaboration mode:** delivery
  - `delivery` → fix directly, explain briefly
  - `learning` → explain the root cause first, guide me to the fix, give the full patch only when I ask
- **Commands** (from `docs/TECH_STACK.md` §9; Android commands live since M0, backend/console from M10/M11):
  - Install: Android — Android Studio Gradle sync · `backend/`, `admin/` — `npm ci`
  - Run (dev): Android — Run ▶ or `./gradlew installDebug` (in `android/`) · backend + DB — `docker compose up` · console — `npm run dev` (in `admin/`)
  - Test: `./gradlew test` (in `android/`) · `npm test` (in `backend/`)
  - Lint / typecheck: `./gradlew lint` · `npx biome check` (in `backend/`, `admin/`)
  - Seed data: debug fake-usage scenarios on the phone (M4); dev config seed for the backend (M10)

---

## 1. Prime directives (always on)

- **Phase gates are real.** Don't move past a phase until its exit gate is met and I approve.
- **Keep it simple (KISS).** The simplest solution that meets the requirements wins. Someone new to the repo must be able to read it, understand it and find their way around without help. Nothing gets built just because an example or template had it. Full rules in §4.
- **Backend and frontend stay separate.** Always separate top-level folders. Structure scales with the size tier (§4) — and stays really simple for simple apps.
- **No implementation code before Phase 5.** Only exception: the mock-data UI demo in Phase 2.
- **Ask, don't assume.** Ambiguous requirement → ask. If you must proceed, log the assumption (`A-##`) in `DISCUSSION.md` and flag it in your reply.
- **Don't invent.** No guessed APIs, config keys, CLI flags or versions. Check official docs or the installed package. If you can't verify, say so.
- **No new dependency without approval.** Justify each: what it does · why it's needed · cost of going without · license.
- **No destructive actions without explicit confirmation:** deleting files/folders, dropping/resetting databases, `git push --force`, rewriting history, migrations on shared environments.
- **Small, scoped diffs.** Touch only what the task needs. No drive-by refactors — put them in the parking lot.
- **Plan before big changes.** More than 3 files, or any schema/API change → list files and approach first.
- **Verified or not done.** Never mark a task done without running it and its tests. Say exactly what was and wasn't verified.
- **Stop thrashing.** After 2 failed attempts at the same fix: stop, list hypotheses with evidence, ask.
- **Completeness duty.** Flag production concerns I didn't mention (validation, indexes, rate limits, logging, empty/error states, CORS…). Never silently omit them, never silently add them.
- **Secrets stay out** of code, logs, commits and chat. Env vars only; keep `.env.example` current.
- **Be direct.** If an idea is flawed or won't scale, say so and explain the mechanism.

---

## 2. Living docs

| File | Created in | Holds |
|---|---|---|
| `docs/REQUIREMENTS.md` | Phase 1 | Scope, users, stories, NFRs, out of scope |
| `docs/DISCUSSION.md` | Phase 2 | Flows, screens, open questions, assumptions, decision log, change requests, parking lot |
| `docs/TECH_STACK.md` | Phase 3 | Chosen stack + rationale, size tier, dependency register |
| `docs/ARCHITECTURE.md` | Phase 4 | HLD & LLD: components, data flows, repo map, data model, API contracts, permissions, config |
| `docs/ROADMAP.md` | Phase 4 | Test strategy, milestones, out of scope |
| `docs/PROGRESS.md` | Phase 5 | Current state, blockers, append-only session log |
| `README.md` | M0 | Setup → run → test → deploy |
| `<folder>/README.md` | M0 | One per backend / frontend / service: what it is · how to run · how to test |

**Rules**
- Update docs yourself when a decision locks, a task completes, or something is learned. Mention it in one line at the end of your reply.
- Docs win over chat. If they conflict, follow the docs or ask.
- Keep "Current state" in `PROGRESS.md` under ~15 lines. Summarise old log entries when the file gets long.
- IDs everywhere so things can be referenced: `US-##` stories · `Q-##` questions · `A-##` assumptions · `D-##` decisions · `CR-##` change requests · `BUG-##` bugs.

**`PROGRESS.md` template**

```text
## Current state
- Phase: 5 — Build · Milestone: M3 Auth (in progress)
- Last done: …
- Next step: …
- Blockers: …
- Open questions: Q-04, Q-07

## Session log (append-only)
### 2026-10-15
- Done: …
- Decided: D-09 …
- Tried & abandoned: … (why)
- Next: …
```

---

## 3. Session protocol

**Start of every session**
- Read `AGENTS.md` → `docs/PROGRESS.md` → current milestone in `docs/ROADMAP.md` → the relevant part of `docs/ARCHITECTURE.md`.
- Open with a 2-line orientation: where we are, what's next.
- No docs yet → new project → start Phase 1.

**End of session** (or when I say `handoff`)
- Tick checkboxes in `ROADMAP.md`, flip diagram nodes in `ARCHITECTURE.md`, update `PROGRESS.md`.
- Log what was done, decisions made, what was tried and abandoned (and why), the single next step.
- Reply with: what works · what's broken · next step.

---

## 4. Engineering principles & repo structure

> **The test:** a developer who has never seen this repo can open it, understand how it works and find any feature within minutes — without asking anyone. Every structural decision is judged against this.

### Principles
- **KISS — keep it simple.** The simplest solution that meets the requirements wins. Complexity must be justified by a requirement, never by "we might need it later".
- **YAGNI — you aren't gonna need it.** Build only what `REQUIREMENTS.md` asks for. No speculative layers, plugins, config options or abstractions.
- **Readability over cleverness.** Boring, descriptive names (`unpaidInvoicesForCustomer`, not `data2`). Flat over nested; early returns over `else` ladders.
- **Single responsibility.** One purpose per file, function and module. If a file passes ~250 lines or a UI component takes more than ~5 props, split it.
- **Separation of concerns.** Routes don't talk to the database directly; UI components don't hold business rules.
- **DRY — only after the third repetition.** Two similar blocks can stay duplicated. A premature abstraction costs more than a little duplication.
- **Consistency beats preference.** Follow the patterns and naming already in the repo before inventing new ones.
- **Explicit over magic.** No hidden global state, no clever metaprogramming, as little indirection as possible.
- **Comments explain *why*, not *what*.** If code needs a comment to explain what it does, rewrite the code.

### Navigability
- **Predictable names** — the same pattern everywhere, e.g. `invoice.routes.js`, `invoice.service.js`, `invoice.model.js`, `invoice.test.js`.
- **Feature folders** once there are more than a couple of features: `features/invoices/` holds that feature's routes, service, model, validation and tests — not a giant global `controllers/` bin.
- **Shallow trees** — no more than ~4 folder levels deep.
- **A `README.md` in every top-level folder and every service:** what it is · how to run it · how to test it.
- **The repo map** in `docs/ARCHITECTURE.md` says where everything lives and where to go to change something.

### Repo structure — scale to the project

**Always**
- Backend and frontend live in **separate top-level folders**, each with its own dependencies, config, Dockerfile and README.
- They talk **only through the API contract** — never import code across the boundary.
- If contracts or types must be shared, they go in one explicit `shared/` folder that holds no business logic.
- **Pick the smallest tier that meets the requirements** and record it in the Project card. Move up a tier only when a requirement forces it — never "for future scale".

**Simple** — one backend, one UI, a handful of features. Keep it really simple: flat folders are fine.

```text
project/
├── backend/
│   ├── src/
│   │   ├── routes/
│   │   ├── models/
│   │   └── server.js
│   ├── package.json
│   └── README.md
├── frontend/
│   ├── src/
│   │   ├── pages/
│   │   ├── components/
│   │   └── api.js
│   ├── package.json
│   └── README.md
├── docs/
├── AGENTS.md
└── README.md
```

**Standard** — many modules, auth, RBAC, dashboards (e.g. an ERP). Feature folders on both sides.

```text
project/
├── backend/
│   ├── src/
│   │   ├── features/
│   │   │   ├── auth/
│   │   │   ├── users/
│   │   │   └── invoices/        ← routes, service, model, validation, tests
│   │   ├── middleware/          ← auth, RBAC, validation, error handler
│   │   ├── config/
│   │   ├── lib/                 ← logger, DB connection
│   │   └── server.js
│   ├── seeds/
│   ├── Dockerfile
│   └── README.md
├── frontend/
│   ├── src/
│   │   ├── features/
│   │   │   ├── auth/
│   │   │   └── invoices/        ← pages, components, hooks, API calls
│   │   ├── components/          ← shared UI only
│   │   ├── mocks/
│   │   └── main.jsx
│   ├── Dockerfile
│   └── README.md
├── docs/
├── docker-compose.yml
├── AGENTS.md
└── README.md
```

**Complex** — microservices and/or multiple apps. Each service gets its own parent folder and runs its own server.

```text
project/
├── services/
│   ├── auth-service/            ← own server, deps, Dockerfile, README, tests
│   ├── inventory-service/
│   └── billing-service/
├── gateway/                     ← single entry point for clients (only if needed)
├── frontends/
│   ├── web-app/
│   └── admin-console/
├── shared/                      ← API contracts / event schemas only
├── infra/                       ← docker-compose, deployment config
├── docs/
├── AGENTS.md
└── README.md
```

- Each service is self-contained and can be cloned, run and tested on its own.
- Services never read each other's database — they talk through APIs or events.

**Mobile:** the Android/iOS project is the frontend. Its backend, if any, lives in its own folder or repo under the same rules.

*Folder and file names above use JavaScript conventions — follow the chosen stack's conventions.*

---

## Phase 1 — Scope & requirements

**Goal:** agree on what we're building and how we'll know it's done.

**Agent does**
- Interview me in rounds of ≤7 questions — not one wall of questions.
- Turn vague words into measurable ones: "fast", "simple", "secure", "all reports", "scalable".
- Separate needs from solutions ("I need Kafka" → what problem is that solving?).

**Output — `docs/REQUIREMENTS.md`**
- Problem statement (2–3 lines) and goal
- Success metrics — how we'll know it works
- Users & roles — what each role can and can't do
- Features with priority — MoSCoW (Must / Should / Could / Won't)
- User stories — `US-##: As a <role>, I want <action>, so that <value>`
  - Acceptance criteria in Given / When / Then
- Non-functional requirements (numbers wherever possible)
  - Performance (response times), scale (users, records per year), availability
  - Security & compliance (data-protection law, payments, audit needs)
  - Privacy & data retention
  - Platforms, devices, browsers, min OS / SDK versions
  - Offline / poor-network behaviour
  - Localisation: language, currency, time zones, number/date formats
  - Accessibility
- Constraints: deadline, budget, hosting, existing systems, integrations, team skills
- Out of scope — explicit list
- Glossary of domain terms

**Exit gate**
- [ ] Every Must-have has a user story with acceptance criteria
- [ ] NFRs have numbers where possible
- [ ] Out of scope is written down
- [ ] I've approved v1

---

## Phase 2 — Brainstorm, flows & clarification

**Goal:** see the shape of the product and remove every vague point before architecture.

**Agent does**
- Map user flows per role (Mermaid flowcharts).
- Build a screen inventory: every screen/dashboard · data shown · actions allowed · who sees it.
- Draw state machines for every entity with a status (e.g. `draft → submitted → approved → paid`).
- Sketch the API surface the screens imply.
- For hard parts, give 2–3 approaches with trade-offs and recommend one — the simplest one that works, unless a requirement rules it out.
- Think like a senior engineer: name bottlenecks — race conditions, N+1 queries, huge lists, file uploads, long-running jobs, third-party rate limits.

**Ambiguity checklist — run on every feature**
- Who can do it? Who can't?
- Inputs, validation rules, outputs?
- Empty, duplicate, invalid, partial-failure cases?
- Limits: size, count, rate, pagination?
- Delete = soft or hard? Needs history / audit trail?
- Two users editing the same record at once?
- Notifications — to whom, which channel, when?
- Time zones, currency, rounding?
- Offline / slow network (mobile)?
- Data retention, export, import?
- Multi-tenant / multi-branch / multi-company?

**Optional — mock-data UI demo**
- Build real frontend screens in the actual `frontend/` (or app module), fed only from a `mocks/` layer.
- No API calls, no hard-coded values inside components — swapping mocks for real calls later touches one layer.
- Walk through it with me and record: what's missing, what screen we now realise we need, what changed.

**Output — `docs/DISCUSSION.md`**
- Flows, screen inventory, state machines, API surface sketch
- Risks
- Open questions `Q-##` (status: open / answered)
- Assumptions `A-##`
- Decision log `D-##`: decision · reason · alternatives considered · date
- Parking lot (good ideas for later)

**Exit gate**
- [ ] No open blocking questions
- [ ] Screen inventory settled
- [ ] `REQUIREMENTS.md` updated with anything new

---

## Phase 3 — Tech stack & dependency review

**Goal:** the most feasible stack for these requirements — not the trendiest.

**Agent does**
- Evaluate against: requirement fit (NFRs, platform, offline, realtime) · my existing skills · ecosystem maturity & LTS · hosting & running cost · licensing · long-term maintainability.
- Present 2–3 options in a comparison table; recommend one with reasons.
- Verify current stable versions from official sources and pin exact versions. Don't rely on training memory.
- Decide the **size tier** (§4) and repo layout, then package manager, runtime versions, database, auth approach, hosting target, CI.
- **Fewest dependencies wins.** If the standard library or ~20 lines of local code does the job, propose that. Use packages for genuinely hard things (auth, crypto, parsing, dates).

**Output — `docs/TECH_STACK.md`**
- Chosen stack + rationale; rejected options and why
- Size tier + why it's the smallest one that meets the requirements
- Dependency register — every package on its own line:

| Package | Version | Layer | Purpose | Cost without it | License |
|---|---|---|---|---|---|

**Exit gate**
- [ ] I approve the stack, the size tier and every dependency
- [ ] Nothing outside the register gets installed — new needs mid-build go through the same approval

---

## Phase 4 — Architecture (HLD/LLD) & roadmap — the most important docs

**Goal:** a blueprint clear enough that any human or agent, in any fresh session, understands how data flows through the system and can pick up the next task cold.

### A. `docs/ARCHITECTURE.md` — HLD & LLD

Written for a human who is new to the repo: after ~10 minutes of reading, they should know how a request travels through the system and where every piece lives. **Scale it to the project** — a simple app may need just one diagram, one data flow and the repo map.

**HLD — high-level design** (written in Phase 4)
- **System overview** — what the system does; who and what uses it (roles, external systems)
- **Component diagram** (Mermaid) — frontend(s), backend/services, DB, cache, queue, third-party APIs, and how each talks to the others (REST, WebSocket, events). Nodes classed `:::pending` / `:::done`, flipped as components land
- **Data flows** (Mermaid sequence diagrams) for the 3–5 most important journeys — e.g. login, the core transaction, a dashboard load — showing the full path:
  `UI → API client → route → middleware (auth, RBAC, validation) → controller → service → model → DB → response → UI`
- **Cross-cutting concerns** — how auth, RBAC, logging, errors and config work across the whole system
- **Permission matrix** — role × action (drives RBAC code and tests)
- **Config & environments** — every env var and its purpose (mirrors `.env.example`); dev / staging / prod; Docker topology (services, ports, volumes, networks)
- **Repo map** — where things live and where to go to change something:

| I want to… | Go to |
|---|---|
| Add an API endpoint | `backend/src/features/<feature>/<feature>.routes.js` |
| Change who can do something | `backend/src/middleware/rbac.js` + permission matrix above |
| Add a screen | `frontend/src/features/<feature>/` |
| Change seed data | `backend/seeds/` |

**LLD — low-level design** (per module, written at the **start** of its milestone, before code)
- Purpose of the module and its folder
- Files and their responsibilities (route / controller / service / model / validation / UI)
- Data model — fields, types, required, relations, indexes, unique constraints + ERD (Mermaid)
- API contracts — method · path · permission · request · response · error cases; one standard response & error shape across the system
- Key logic — business rules, state machines, a sequence diagram for anything non-trivial
- Frontend — screens, who owns which state, which API calls each screen makes
- Module-specific error handling and edge cases

**Rule:** code and diagrams must agree. If a task changes a component, a data flow or a folder, update `ARCHITECTURE.md` in the same task.

### B. `docs/ROADMAP.md` — test strategy & milestones
- **Test strategy** — what's tested at which level, tools, coverage target for critical paths
- **Out of scope**
- **Milestones**
  - Each milestone is a **vertical slice**: independently verifiable, finishable in 1–2 sessions.
  - Order by dependency: foundation → skeleton → backbone → auth → RBAC → domain modules → dashboards → integrations → hardening → deploy → UAT — keeping only the ones the requirements need.
  - Every milestone cites the story IDs (`US-##`) that justify it.

**Milestone template**

```text
### M3 — Authentication            status: pending | in-progress | done
Goal: users can sign up, log in, log out; protected routes enforce auth.
Depends on: M2
Stories: US-01, US-02
Tasks:
- [ ] LLD for this module in ARCHITECTURE.md (before code)
- [ ] Backend: …
- [ ] Frontend: …
- [ ] Seed data: one user per role
- [ ] Tests: …
Done when:
- [ ] …
Verify with: <test command> · <curl …> · manual steps …
Not in this milestone: password reset (→ M5)
```

**Reference sequence — web app (e.g. ERP on MERN)**

| M | Milestone | Includes |
|---|---|---|
| M0 | Foundation | Repo, `.gitignore`, folder layout per §4 (separate `backend/` and `frontend/`), lint/format, env config, Docker Compose, CI skeleton, READMEs |
| M1 | Walking skeleton | Server + client + DB running and connected; health endpoint shown in UI; one end-to-end request |
| M2 | Backbone | Config validation at boot, structured logging + request IDs, central error handler, validation middleware, response format, security headers/CORS, frontend layout/routing/API client, seed script v1 |
| M3 | Auth | Sign-up/login/logout, session or token strategy, password hashing, protected routes (BE + FE) |
| M4 | RBAC | Roles & permissions model, authorise middleware, permission-aware UI, role × route test matrix |
| M5 | Admin console | User & role management, settings, audit log (BE + FE) |
| M6…Mn | Domain modules | One per milestone: LLD → schema → validation → service → API → tests → UI → seed data |
| Mn+1 | Dashboards & reports | Aggregation queries + indexes, charts, filters, exports |
| Mn+2 | Integrations & jobs | Email/SMS, file uploads, background jobs, webhooks, third-party APIs |
| Mn+3 | Hardening | Security review, performance, empty/loading/error states, accessibility |
| Mn+4 | Deploy & observe | Production build, CI/CD, staging, monitoring, error tracking, backups |
| Mn+5 | UAT & release | User acceptance testing, bug bash, docs, `v1.0.0` |

**Reference sequence — Android app**

| M | Milestone | Includes |
|---|---|---|
| M0 | Project setup | Gradle version catalog, modules, build variants/flavours, lint/static analysis, CI |
| M1 | Skeleton | Navigation, theme, DI, one screen ↔ one ViewModel ↔ fake repository |
| M2 | Data layer | Local DB, network client, repository pattern, result/error wrapper, logging |
| M3 | Auth & session | Secure token storage, session expiry, logout everywhere |
| M4…Mn | Feature modules | One vertical slice each |
| Mn+1 | Platform features | Offline & sync, runtime permissions with rationale, notifications, background work |
| Mn+2 | Release | Signing, shrink/minify, size check, versioning, internal testing track |

> ### ⚠️ IMPORTANT — these sequences are patterns, not defaults
> - **Include a milestone only if a requirement in `REQUIREMENTS.md` calls for it.**
> - No RBAC, admin console, dashboards, background jobs or microservices just because the reference had them.
> - A simple app may need only M0, M1, a couple of feature milestones and a release milestone — and that's correct.
> - If you're unsure whether a milestone is needed, ask; don't add it "to be safe".

**Exit gate**
- [ ] I approve `ARCHITECTURE.md` (HLD) and `ROADMAP.md`
- [ ] Every Must-have story maps to a milestone
- [ ] Every milestone cites the stories that justify it — nothing is there only because the reference sequence had it

---

## Phase 5 — Build (milestone loop)

- One milestone at a time, one task at a time. Don't start the next milestone until the current one is done and verified.
- Start each module milestone by writing its LLD in `ARCHITECTURE.md`.
- New idea mid-milestone → parking lot, not the current diff.
- Once an endpoint is verified, replace that screen's mock with the real call.

**Task loop**
1. Restate the task and list the files you'll touch
2. Implement the smallest working slice — the simplest version that meets the acceptance criteria
3. Write or update tests for it
4. Run lint, typecheck, tests; prove the behaviour (test, curl, emulator)
5. Self-review the diff: simplicity (§4), edge cases, security, leftover debug logs, TODOs, hard-coded values
6. Tick the checkbox in `ROADMAP.md`; update `PROGRESS.md`; update `ARCHITECTURE.md` if a component, flow or folder changed
7. Commit
8. Report: what changed · how it was verified · next step

**Definition of Done (every task)**
- [ ] Meets acceptance criteria
- [ ] Builds; lint and typecheck clean
- [ ] Tests written and passing
- [ ] Follows §4 — simple, readable, in the right folder
- [ ] No secrets, no debug leftovers
- [ ] Docs updated — `ARCHITECTURE.md` matches the code
- [ ] Committed

**Code rules on top of §4**
- Validate at every boundary: API input, forms, external data.
- Authorisation is always enforced server-side — hiding UI is UX, not security.
- Handle errors explicitly; no silent catches.

---

## Track A — Sample & seed data (runs alongside the build)

**When**
- Phase 2 → static mock data for the UI demo (`mocks/`)
- M1–M2 → seed script v1, extended with every module
- Hardening → volume data for performance tests

**Rules**
- Generate through the real schema/validation so seeds can't drift from the models.
- **Deterministic:** fixed random seed (e.g. Faker seed) → identical data every run.
- **Idempotent:** safe to rerun. `seed:reset` for a clean slate — dev only, never production.
- **Realistic & locale-correct:** names, phone formats, addresses, currency, time zones of the target users.
- **Coverage:** every role, every status in every state machine, real relations (orders with items, users across departments).
- **Edge-case set:** empty values, max lengths, unicode/emoji, special characters, boundary numbers, past/future dates, near-duplicates.
- **Volume set:** a flag for N rows (e.g. 10k / 100k) to test pagination, indexes, dashboards.
- Known dev login per role, documented in README (dev only).
- Public datasets: check the license. Never use production or real personal data; anonymise anything derived from real data.

**Output:** `seeds/` (or `scripts/seed`) + the command in the Project card.

---

## Track B — Testing & QA (every milestone, not at the end)

**Rules**
- Derive test cases from acceptance criteria; put story IDs in test names.
- Bug fix → reproduce with a failing test first, then fix.
- Never delete, skip or weaken a failing test to make it pass. Report it instead.
- CI runs lint + tests on every push. Red CI blocks milestone completion.

**Layers**
- **Unit:** business logic, validators, utilities
- **Integration / API:** every endpoint — success, validation error, unauthenticated, forbidden
- **E2E:** critical journeys only (login, core transaction, admin flow)
- **Mobile:** unit tests + UI tests; run on an emulator and at least one real device
- **RBAC matrix:** every role × every protected action → expected allow / deny

**Per milestone:** give me a short manual QA checklist (steps + expected result).

**Before release**
- Dependency vulnerability audit
- Basic load test against the NFR numbers
- Accessibility check
- Cross-browser / cross-device check

**Bug report format**

```text
BUG-## · <title>
Steps: …
Expected: …
Actual: …
Env: browser/device, OS, build
Evidence: logs / screenshot
Severity: blocker | major | minor
```

---

## Phase 6 — Hardening, deployment & handover

- **Security:** OWASP Top 10 basics, auth flows, rate limits on auth endpoints, input validation, security headers, CORS allowlist, dependency audit, secrets scan.
- **Performance:** index every frequent query, paginate every list, no N+1, cache only where measured.
- **Observability:** structured logs to stdout, request IDs, health/readiness endpoints, error tracking, audit log for admin actions.
- **Deployment:** environment-specific config, migration strategy, CI/CD, staging before production, rollback plan, backups + a tested restore.
- **Mobile release:** signing keys outside the repo, shrink/minify, versioning scheme, store listing and privacy policy, staged rollout.
- **Handover:** README (setup → run → test → deploy), folder READMEs, API docs, `.env.example`, `ARCHITECTURE.md` current, known issues, CHANGELOG.

**Exit gate**
- [ ] Release checklist complete
- [ ] A fresh reader can follow `ARCHITECTURE.md` and the READMEs to run the project and find any feature
- [ ] I've completed UAT
- [ ] `v1.0.0` tagged

---

## Cross-cutting rules

### Change control
- Any new or changed requirement after Phase 1 → log `CR-##` in `DISCUSSION.md` with its impact on data model, API, UI, tests and timeline.
- Implement only after I approve. Update `REQUIREMENTS.md`, `ARCHITECTURE.md` and `ROADMAP.md` first, code second.

### Git
- Initialise in M0. Commit after each verified task.
- Branch per milestone: `feat/m3-auth`; merge when the milestone is done.
- Conventional commits: `feat:` `fix:` `refactor:` `test:` `docs:` `chore:`
- Tag milestones (`m3-done`) and releases (`v1.0.0`).
- Never commit `.env`, keystores, credentials or large generated files.

### Security baseline
- Passwords hashed with a slow hash (bcrypt / argon2).
- Parameterised queries or ORM — never build queries from strings.
- Least privilege for DB users, API keys and app permissions.
- Mobile: no API secrets inside the app binary; tokens in platform secure storage.
- Log events, never passwords, tokens or full personal data.

### Context hygiene (AI sessions)
- Start from the repo map in `ARCHITECTURE.md` and the relevant doc section instead of re-scanning the whole codebase.
- Aim for one milestone per session; start a fresh session rather than letting context bloat.
- Switching agents or tools? The docs are the handoff — keep them current.

---

## Quick commands

| I type | You do |
|---|---|
| `status` | 2-line orientation from `PROGRESS.md` |
| `start phase <n>` | Begin that phase, following its section |
| `next` | Take the next unchecked task in the current milestone and run the task loop |
| `gate` | Check the current phase's exit gate; list what's missing |
| `flow <journey>` | Explain how data flows for that journey with a sequence diagram; add it to `ARCHITECTURE.md` |
| `arch` | Check `ARCHITECTURE.md` against the code; list and fix any drift |
| `simplify` | Review the current milestone's code against §4; propose simplifications |
| `CR: <change>` | Run change control |
| `bug: <description>` | Write a bug report, reproduce with a failing test, propose the fix |
| `seed` | Extend seed data for the current milestone |
| `qa` | Manual QA checklist for the current milestone |
| `handoff` | End-of-session protocol |
