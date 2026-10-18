## Current state
- Phase: 5 — Build · Milestone: M0 Foundation (in progress), branch `feat/m0-foundation`
- Last done: Android CI workflow written (not yet run); lint + JaCoCo coverage; earlier: KSP check passed, `android/` project created by the wizard and aligned with TECH_STACK (AGP 9.4.1, Kotlin 2.4.21, BOM 2026.09.00, SDK 30/36/37); the version catalog matches the register exactly
- Next step: push `feat/m0-foundation` and confirm the first Android CI run is green; then `android/README.md`
- Blockers: none
- Open questions: Q-04 (success-metric targets, not blocking)
- Test devices: emulator + Pixel 7a

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
