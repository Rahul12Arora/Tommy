# android/

The Nyx Android app (`com.companionstudio.nyx`) — Kotlin + Jetpack Compose, one `app` module. It does all tracking and reacting on the phone and works offline. How it's built: [`docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md); where to change things: the repo map there.

## What you need

- **Android Studio** Rabbit 1 or newer. It brings the Android SDK and its own JDK.
- **For terminal builds:** any JDK 17+ on your `PATH` (e.g. `brew install openjdk`). It only starts Gradle — the Gradle daemon itself runs on **JDK 25**, as set in `gradle/gradle-daemon-jvm.properties`. Gradle uses an installed JDK 25 if it finds one, otherwise downloads it once.
- A device: an emulator, or a phone on Android 11+ (API 30) with USB debugging on.

## Run

- **Android Studio:** open this `android/` folder (not the repo root), let Gradle sync, press Run ▶.
- **Terminal:** `./gradlew installDebug` installs on the connected device.

## Test and check

Run from this folder.

| What | Command | Notes |
|---|---|---|
| Everything CI runs | `./gradlew assembleDebug test lint` | Same as `.github/workflows/android.yml` |
| Unit tests | `./gradlew test` | `app/src/test/` |
| Device tests | `./gradlew connectedAndroidTest` | `app/src/androidTest/`; needs an emulator or phone |
| Lint | `./gradlew lint` | Warnings fail the build. Report: `app/build/reports/lint-results-debug.html` |
| Unit-test coverage | `./gradlew createDebugUnitTestCoverageReport` | Report: `app/build/reports/coverage/test/debug/index.html`. Targets in `docs/ROADMAP.md` |

## Debug scenarios (debug builds only)

Scripted usage instead of real usage, optionally with time running faster, so tracking can be checked in seconds ([`docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md) §14.4). Scenario files: [`app/src/debug/assets/scenarios/`](app/src/debug/assets/scenarios/). Finish setup (or open the app) first, so the pet service is running.

| What | Command |
|---|---|
| Play a scenario at 60× | `adb shell am broadcast -n com.companionstudio.nyx/.debug.ScenarioReceiver --es scenario instagram-65min --ei speed 60` |
| Back to real usage and time | `adb shell am broadcast -n com.companionstudio.nyx/.debug.ScenarioReceiver --ez stop true` |
| Watch the tracker | `adb logcat -s Nyx` |

Both commands clear all tracked time first. With more than one device connected, add `-s <serial>` after `adb` (and set `ANDROID_SERIAL` for Gradle's device tasks).

## Versions and dependencies

All versions live in [`gradle/libs.versions.toml`](gradle/libs.versions.toml), which mirrors the dependency register in [`docs/TECH_STACK.md`](../docs/TECH_STACK.md) §6. Adding or changing a dependency needs approval first (see `AGENTS.md`).

- AGP 9 compiles Kotlin itself; the top-level `build.gradle.kts` opts it into our Kotlin version.
- Lint's "newer version available" checks are off on purpose — versions change only through approval.

## Built-in config

The app's built-in config is [`../shared/default-config.json`](../shared/default-config.json). The build copies it into the app's assets (`copyDebugDefaultConfig` / `copyReleaseDefaultConfig`), so edit it there — there's no copy under `android/` to change. Config unit tests read `../shared/fixtures/` directly.
