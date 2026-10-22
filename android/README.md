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

Scripted usage instead of real usage, optionally with time running faster, so tracking and mood can be checked in seconds ([`docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md) §14.4, §14.6). Scenario files: [`app/src/debug/assets/scenarios/`](app/src/debug/assets/scenarios/). Finish setup (or open the app) first, so the pet service is running.

| What | Command |
|---|---|
| Play a scenario at 60× | `adb shell am broadcast -n com.companionstudio.nyx/.debug.ScenarioReceiver --es scenario instagram-65min --ei speed 60` |
| Walk the mood down to 🐾 (4 h of Instagram, ~4 min at 60×) | `adb shell am broadcast -n com.companionstudio.nyx/.debug.ScenarioReceiver --es scenario instagram-4h --ei speed 60` |
| Log one mood event now (e.g. to bring a pet that ran away back) | `adb shell am broadcast -n com.companionstudio.nyx/.debug.ScenarioReceiver --es event break_taken` |
| Back to real usage and time | `adb shell am broadcast -n com.companionstudio.nyx/.debug.ScenarioReceiver --ez stop true` |
| Watch the tracker, mood and bubbles | `adb logcat -s Nyx` |

Playing a scenario and going back to real usage both clear all tracked time and the event log first; `event` clears nothing. With more than one device connected, add `-s <serial>` after `adb` (and set `ANDROID_SERIAL` for Gradle's device tasks).

## Against the local backend (debug builds only)

Debug builds fetch the config from `http://127.0.0.1:3000` ([`docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md) §14.12). The same steps work for the emulator and a USB phone:

1. Start the backend: `docker compose up` in the repo root ([`backend/README.md`](../backend/README.md)).
2. Forward the device's port 3000 to the laptop: `adb reverse tcp:3000 tcp:3000`. Run it again whenever the phone reconnects or the emulator restarts. Without it, checks fail as if the phone were offline.
3. Open the app and watch `adb logcat -s Nyx`.

The app checks when the pet service starts, then every 6 hours while the screen is on. To check now: `adb shell am force-stop com.companionstudio.nyx`, then open the app.

| Log line | Meaning |
|---|---|
| `Config 2 applied` | A new published version is saved and in use from the next tick |
| `Config 2 unchanged (304)` | The server has nothing newer |
| `Fetched config ignored: setting-range at nags.snoozeMinutes` | The published document failed the app's checks; the last good one stays |
| `Config check failed: …` | Server down, no `adb reverse`, offline, or an HTTP error; the next try is 6 hours later |

**Forcing an invalid published document** (dev database only). The console and backend refuse invalid documents, so this goes around them. It supersedes the live version and publishes the next one with an out-of-range snooze default:

```bash
docker compose exec db psql -U nyx -d nyx -c "
WITH live AS (
  UPDATE config_versions SET status = 'superseded'
  WHERE schema_version = 1 AND status = 'published'
  RETURNING document
), next AS (SELECT max(id) + 1 AS n FROM config_versions)
INSERT INTO config_versions (id, schema_version, status, document, published_at)
SELECT next.n, 1, 'published',
       jsonb_set(jsonb_set(live.document, '{configVersion}', to_jsonb(next.n)), '{nags,snoozeMinutes,default}', '400'),
       now()
FROM live, next;"
```

The app logs `Fetched config ignored: schema at nags.snoozeMinutes.default, setting-range at nags.snoozeMinutes` (400 is outside both the schema's bounds and the setting's range) and keeps its version. To go back, publish a valid version from the console, or reuse the SQL above with the last good row's document instead of the broken one.

## Versions and dependencies

All versions live in [`gradle/libs.versions.toml`](gradle/libs.versions.toml), which mirrors the dependency register in [`docs/TECH_STACK.md`](../docs/TECH_STACK.md) §6. Adding or changing a dependency needs approval first (see `AGENTS.md`).

- AGP 9 compiles Kotlin itself; the top-level `build.gradle.kts` opts it into our Kotlin version.
- Lint's "newer version available" checks are off on purpose — versions change only through approval.

## Built-in config

The app's built-in config is [`../shared/default-config.json`](../shared/default-config.json). The build copies it into the app's assets (`copyDebugDefaultConfig` / `copyReleaseDefaultConfig`), so edit it there — there's no copy under `android/` to change. Config unit tests read `../shared/fixtures/` directly.
