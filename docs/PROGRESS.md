## Current state
- Phase: 5 — Build · Milestone: M2 Config (in progress, branch `feat/m2-config`)
- Last done: `shared/fixtures/` — 3 valid, 17 invalid (every rule; 8 `schema` cases)
- Next step: Gradle copies `shared/default-config.json` into app assets (+ `nyx.sharedDir` for unit tests)
- Blockers: none
- Open questions: Q-04 (success-metric targets, not blocking)
- Test devices: emulator (API 37) + Pixel 7a
- Build setup: Homebrew JDK on PATH starts Gradle; daemon runs on JDK 25 (`android/README.md`)

## Session log (append-only)

### 2026-10-18
- Done: Phase 1 requirements (v1.2), Phase 2 flows/screens/state machines, Phase 3 tech stack, Phase 4 architecture (HLD) + roadmap — all approved
- Done: repo skeleton committed (2 commits) and pushed from the developer's Mac
- Decided: D-01 to D-49 (see `docs/DISCUSSION.md`), CR-01 to CR-05 — app renamed from Tommy to Nyx, ID `com.companionstudio.nyx` (D-49)
- Tried & abandoned: building and pushing from the cloud planning session — its network blocks Google's and Maven Central's repositories (Gradle can't resolve the Android plugin), and Claude has no push access to the repo; M0's Android part runs in Android Studio instead
- Next: Android project via the wizard → align versions → KSP check → lint + coverage → CI → `android/README.md` → verify `./gradlew assembleDebug test lint` + emulator → merge `feat/m0-foundation`, tag `m0-done`

### 2026-10-19
- Done: Android project aligned with TECH_STACK. Kotlin 2.4.21 comes in through the root `buildscript` classpath (the method in AGP 9's release notes). `assembleDebug`, `test` and `assembleDebugAndroidTest` pass with the Android Studio JDK
- Removed wizard extras that aren't in the register: `espresso-core`, `lifecycle-runtime-ktx`, `ui-graphics` (comes with `ui`)
- Decided: CR-06 — AGP 9.4.0 → 9.4.1 (the wizard's default patch release)
- Done: root `.idea/` (from opening the repo root in the IDE) is gitignored; `android/.idea` project files stay uncommitted
- Learned: terminal builds now use Homebrew JDK 27 on PATH as the Gradle launcher; the Gradle daemon runs on the JDK 25 toolchain set in `android/gradle/gradle-daemon-jvm.properties`
- Next: KSP check → lint + coverage → CI → `android/README.md`
- Done: KSP 2.3.12 confirmed with Kotlin 2.4.21 + AGP 9.4.1. KSP plugin and Room runtime/compiler are wired in the app module; a throwaway entity, DAO and database produced `_Impl` classes and were then deleted (the real database comes in M1)
- Done: Android Lint set to fail on warnings. Off: AndroidGradlePluginVersion, GradleDependency, NewerVersionAvailable, OldTargetApi (versions are pinned on purpose; these would fail CI whenever a new release ships). Fixed the wizard's lint warnings: deleted unused `colors.xml` (with approval) and the duplicate activity label
- Done: unit-test coverage on for debug (`createDebugUnitTestCoverageReport`). Verified: lint passes clean, fails (exit 1) on a probe warning; `assembleDebug test lint` passes
- Done: `.github/workflows/android.yml` — on push touching `android/`: JDK 25 + `./gradlew assembleDebug test lint`. Actions pinned by SHA; Gradle cache set to `basic` (the default "enhanced" one is a commercial service). Not yet run on GitHub
- Done: pushed `feat/m0-foundation`; the developer merged it to `main` as PR #1 (`c01419d`) before M0 closed — remaining M0 commits go to `main` through PR #2
- Done: `android/README.md` (requirements incl. JDK 25 daemon, run, test/lint/coverage commands, versions); root README links to it. `shared/README.md` already current
- Verified: first Android CI runs green — branch `ddd3a10` (run 39172675327) and `main` `c01419d` (run 39172872758)
- Verified: the developer ran the app on the emulator — placeholder screen shows "Hello Android!"
- Done: M0 closed — remaining commits merged to `main` through PR #2, tagged `m0-done`
- Next: M1 — LLD first (ARCHITECTURE.md §14), then `PetApp`, `AppContainer`, Navigation 3, Room `AppDatabase`, DataStore, `DayClock`, Home with fake data
- Started M1 on branch `feat/m1-shell-storage` (PR #2 merged M0's close-out into `main`)
- Done: M1 LLD in ARCHITECTURE §14.1, approved
- Decided: D-50 — Room and DataStore are created in M3 with their first table and keys, not empty in M1. Probe: Room refuses a database with no entities. (First proposed as M4; corrected to M3 because S-05 already writes `app_categories`)
- Done: `PetApp`, `AppContainer`, `MainActivity`, `AppNavigation` (Home + placeholder Focus/Trends/Settings), `DayClock`, Home ↔ `HomeViewModel` ↔ `FakeSummaryRepository`, `formatDuration`; wizard example tests deleted; theme boilerplate removed
- Done: serialization plugin applied (back-stack keys); `kotlinx-serialization-json` added directly, since only an old core (1.7.3) arrived transitively
- Decided: CR-07 — `espresso-core` 3.7.0 for device tests only. Compose UI tests pulled in Espresso 3.5.0, which fails on API 35+ (`NoSuchMethodException: InputManager.getInstance`)
- Verified: `./gradlew assembleDebug test lint connectedAndroidTest` green. 12 unit tests (DayClock 7, DurationFormat 4, HomeViewModel 1) and 4 device tests (Home screen 3, navigation + back 1) on the API 37 emulator. DayClock coverage 95% (100% branches). Screenshot: Home shows the fake totals, edge-to-edge
- Not verified yet: the predictive back edge-swipe by hand; CI on GitHub (branch not pushed)
- Next: manual back-gesture check → push → CI → close M1
- Verified: the developer ran the M1 manual QA checklist on the emulator (Home, placeholders, predictive back swipe, rotation, back exits) — all fine
- Verified: Android CI green on `feat/m1-shell-storage` (run 39243292782)
- Done: M1 closed — merged to `main` via PR, tagged `m1-done`
- Next: M2 Config — branch `feat/m2-config`, LLD first
- Started M2: branch `feat/m2-config`; LLD written (§14.2): full document shape, 9 validation rules with IDs, fixtures named by rule, layering as a pure function over user values, Gradle asset copy via `addGeneratedSourceDirectory` (checked against `gradle-api` 9.4.1). Proposed D-51 to D-53, A-28 — waiting for approval
- Approved: M2 LLD with D-51 to D-53, A-28
- Done: schema + default config. Checked with a throwaway Python script (not committed): the subset of draft-07 the schema uses + the cross-field rules; it also rejects a deliberately broken copy for the right reasons. No real JSON Schema validator on this Mac (no Python `jsonschema`, no Node) — a real schema run comes with the backend's Ajv tests (M10); the Kotlin validator checks the defaults from the next tasks on
- Done: fixtures, 3 valid + 17 invalid. Verified with Python `jsonschema` 4.25.1 (draft-07) in a scratchpad virtualenv, not a project dependency: the schema is a valid draft-07 schema; valid files and `default-config.json` pass it; each `schema--*` file fails it for exactly its intended reason; every other invalid file passes the schema and fails exactly its own cross-field rule (checked with a throwaway reference script)
