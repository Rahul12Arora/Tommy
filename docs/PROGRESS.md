## Current state
- Phase: 5 — Build · Milestone: M0 Foundation (in progress), branch `feat/m0-foundation`
- Last done: Phases 1–4 approved; repo skeleton committed — `main` has the docs, `feat/m0-foundation` has README, `.gitignore`, `shared/`, this file
- Next step: create the Android project in `android/` with Android Studio's "Empty Activity" wizard (name Nyx, package `com.companionstudio.nyx`, min SDK 30, Kotlin DSL), then run `next` in Claude Code to align it with `docs/TECH_STACK.md`
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
