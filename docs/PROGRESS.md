## Current state
- Phase: 5 — Build · Milestone: M0 Foundation (in progress), branch `feat/m0-foundation`
- Last done: `android/` project created by the wizard and aligned with TECH_STACK (AGP 9.4.1, Kotlin 2.4.21, BOM 2026.09.00, SDK 30/36/37); the version catalog matches the register exactly
- Next step: confirm KSP 2.3.12 builds with Kotlin 2.4.21 (apply KSP + Room compiler, build)
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
