# Requirements — v1.3 (approved 2026-10-18; CR-01 to CR-05, CR-09 and CR-10 applied)

> Phase 1 output. Decisions (`D-##`), assumptions (`A-##`), open questions (`Q-##`) and risks (`R-##`) live in `docs/DISCUSSION.md`.
> Last updated: 2026-10-21 · Changes after approval go through change control (`CR-##` in `docs/DISCUSSION.md`).

---

## 1. Problem & goal

- **Problem:** people lose hours to scrolling and games without noticing, and find it hard to stay with difficult tasks such as a study video.
- **Goal:** a pet that lives on top of the phone screen, reacts to how the phone is being used, warns when limits are crossed, nudges breaks, and keeps the user company during focus sessions.
- **App name:** Nyx (D-49).

---

## 2. Success metrics (v1)

Targets are proposals — see Q-04.

| Metric | Source | Proposed target |
|---|---|---|
| Social & scrolling time, week 4 vs week 1 (per opted-in device) | Opt-in stats | −20% median |
| Focus sessions completed per active device per week | Opt-in stats | ≥ 3 |
| Day-7 retention | Play Console | ≥ 25% |
| Crash-free users | Play Console (Android vitals) | ≥ 99% |
| Pet still running 24 h after last app open | Opt-in stats | ≥ 90% of opted-in devices |

---

## 3. Users & roles

| Role | Who | Can | Can't |
|---|---|---|---|
| User | Adult (18+) who installs the app | Use every feature on their own phone; change settings the admin marked user-editable, within the allowed range | Change admin-locked settings; see anyone else's data |
| Admin | The developer, through the web admin console | Edit and publish config, pets, personalities, line pools, defaults, ranges, locks, feature switches; view aggregated opt-in stats | See any individual's usage or identity |

---

## 4. Features & priority (MoSCoW)

"Week-1 build" is the cut for the 2026-10-25 working build (D-23).

| ID | Feature | Priority | Week-1 build? |
|---|---|---|---|
| F-01 | Setup flow: 18+ check, permissions, pet & personality, categories, limits & goals, stats opt-in | Must | Yes |
| F-02 | Usage tracking: per-app time → category, continuous stretches, day reset | Must | Yes |
| F-03 | Floating pet overlay with emoji expressions | Must | Yes |
| F-04 | Summary panel on tap: today | Must | Yes |
| F-05 | Limits (daily + continuous) with escalating nags and snooze | Must | Yes |
| F-06 | Break reminders (speech bubble + notification) | Must | Yes |
| F-07 | Focus sessions | Must | Yes |
| F-08 | Mood system, including low point | Must | Yes |
| F-09 | Config layering: built-in → admin → user (ranges, locks) | Must | Built-in + user layers; admin layer arrives with F-10 |
| F-10 | Node.js backend + web admin console + remote config fetch | Must | No — week 2 |
| F-11 | Pets, personalities and line pools as data, managed in the console | Must | Partial — bundled set only |
| F-12 | Goals: limit goals + positive goals | Must | Limit goals only |
| F-13 | 7-day and 30-day trends | Must | No — week 2 |
| F-14 | Opt-in anonymous stats + admin stats view | Must | No — week 2–3 |
| F-15 | Consistent emoji look on every phone (bundled emoji font) | Should | If licence checks out |
| F-16 | Config version history + one-click rollback in the console | Could | No |
| F-17 | Chat with the pet (cloud LLM) | Won't (v1) — post-v1 | — |
| F-18 | Home screen opened from the launcher (CR-03) | Must | Yes |
| F-19 | Hide the pet temporarily (CR-04) | Must | Yes |
| F-20 | Idle lines (CR-02) | Must | Yes |
| F-21 | Permission-lost notification + fix (CR-01) | Must | Yes |

---

## 5. User stories

### Setup — F-01

**US-01** As a user, I want to be walked through each permission the pet needs, so that it works without me hunting through Settings.
- Given a fresh install, when I start setup, then I see one step per permission (usage access, display over other apps, notifications, battery optimisation), each with a one-line reason and a button that opens the exact system screen.
- Given I return from a system screen, when the permission is now granted, then the step ticks itself.
- Given I skip a permission, when setup ends, then the app lists what won't work and lets me grant it later from Settings.
- Given my phone brand restricts background apps, when I reach the battery step, then I see that brand's instructions.

**US-02** As the admin, I want setup to confirm the user is 18+, so that the app matches its adult audience.
- Given setup, when the user says they are under 18, then the app explains it is for adults and setup stops.

**US-03** As a user, I want to choose a pet and a personality, so that the pet feels like mine.
- Given setup, when I reach the pet step, then I see every pet and personality enabled in the config, each with a preview line.
- Given setup is done, when I open Settings, then I can switch pet or personality at any time.

**US-04** As a user, I want my apps sorted into categories automatically, so that I only fix the wrong ones.
- Given setup, when the app lists my installed apps, then each shows a suggested category: the admin default if one exists, otherwise Android's app category mapped to ours, otherwise Neutral.
- When I change an app's category, then all tracking from that moment uses my choice.
- Given I install a new app later, when I first use it, then it gets a suggested category that I can change in Settings.

**US-05** As a user, I want sensible default limits and goals, so that I'm protected from day one.
- Given setup, when I reach limits and goals, then I see the defaults from config and can adjust each one within its allowed range.
- Settings the admin has locked are shown read-only.

### Pet & summary — F-03, F-04

**US-06** As a user, I want the pet floating on top of every app, so that it's always with me.
- Given permissions are granted, the pet appears over every app and the home screen.
- I can drag the pet; on release it snaps to the nearest screen edge.
- The pet never blocks taps outside its own bounds.
- Given the phone restarts, the pet returns without me opening the app.

**US-07** As a user, I want to tap the pet for a quick summary, so that I know where my day stands.
- Given the pet is on screen, when I tap it, then a half ring of small icons opens around it, towards the middle of the screen (CR-09, D-87), showing: total screen time today, productive time vs doomscrolling time, goal progress as one Limits icon showing the goal nearest to or furthest over its limit as used/limit (e.g. `47/60m`), which opens the list of every goal (D-90), continuous screen use (US-30), and the focus timer while a session runs. Start focus, Open Nyx and Hide are icons on the same ring.
- Each icon has a label I can read (and TalkBack reads); "over limit" is shown by more than colour.
- When I tap the pet again or outside the ring, it collapses.
- Numbers are at most 1 minute old when the panel opens.

### Limits & nags — F-05

**US-08** As a user, I want the pet to warn me when I pass a daily limit, so that I notice before an hour turns into three.
- Given a daily limit (per category or total screen time), when I reach it while using an app it covers, then within 60 s the pet glows red and shows a speech bubble in its personality.
- While I stay in apps the limit covers, the nag repeats every N minutes and escalates through the configured steps (glow → bubble → repeat).
- When I leave those apps, nagging stops; it resumes if I return the same day.

**US-09** As a user, I want a warning for long unbroken stretches, so that a single sitting doesn't swallow my evening.
- Given a stretch limit (e.g. 30 min on Social or Games), when I use apps in those categories without a break (screen off ≥ 5 min), then the pet nags as in US-08.
- Only a break resets the stretch; time spent in other apps doesn't.

**US-10** As a user, I want to snooze a nag, so that I can finish what I'm doing.
- Given a nag is showing, when I tap snooze, then nagging pauses for the configured snooze length.
- Each snooze lowers the pet's mood.
- When I've used today's maximum snoozes, the snooze option is hidden until the day resets.
- Snooze silences only the nag it was pressed on; other limits can still nag.

### Breaks — F-06

**US-11** As a user, I want reminders to take breaks, so that I look away from the screen regularly.
- Given a break trigger from config (user-adjustable within range), when it is reached, then the pet shows a speech bubble and a notification is posted.
- When the screen stays off for at least 5 minutes after a reminder, the break counts as taken and the pet's mood rises.

### Focus sessions — F-07

**US-12** As a user, I want to start a focus session for a hard task, so that the pet helps me stick with it.
- When I tap Focus (from the pet panel), then I choose: goal (fixed time / until I'm done / no app-switching for N minutes), allowed app(s), and pet behaviour (quiet presence / check-in every X minutes / reward at the end).

**US-13** As a user, I want the pet to notice when I drift during focus, so that I come back to the task.
- Given an active session, when I switch to an app that isn't allowed, then the pet reacts according to the chosen behaviour and the switch is recorded; for a no-switching goal the session is marked broken.
- Opening the notification shade, the pet panel, or taking a call is not a switch (A-02). Locking the screen pauses the session.
- In check-in mode, the pet shows a check-in bubble every X minutes.
- During a session, limit nags and break reminders are held; any that still apply fire when the session ends.

**US-14** As a user, I want a reward when I finish, so that finishing feels good.
- When the goal is reached (timer done, or I tap Done), then the pet shows a reward and its mood rises.
- When I end early, the session is recorded as incomplete and mood doesn't change.

### Mood — F-08

**US-15** As a user, I want the pet's mood to reflect my habits, so that I can see how I'm doing at a glance.
- Mood is a number on a scale set in config. Completed focus sessions, goals met and breaks taken raise it; doomscrolling time, exceeded limits and snoozes lower it. Amounts come from config.
- The pet's emoji expression follows its mood band (mapping in config).
- At the day reset, mood returns to its starting value.

**US-16** As a user, I want the pet to hit a low point if I neglect my goals, so that there's a real consequence.
- When mood reaches the low threshold, the pet shows its sick/sad expression.
- When mood hits zero, the pet runs away: a paw print 🐾 replaces it on screen.
- The pet returns after a break or a completed focus session, or at the day reset (A-11).

### Goals & trends — F-12, F-13

**US-17** As a user, I want limit goals and positive goals, so that I track what to do less of and more of.
- I can set limit goals (e.g. Social ≤ 1 h/day) and positive goals (e.g. ≥ 2 focus sessions/day, ≥ 1 h Productive/day).
- Progress shows in the summary panel.
- Meeting a positive goal raises mood once per goal per day.
- A limit goal is judged at the day reset: "kept" or "missed" shows in trends. It doesn't change mood, because going over a limit already lowers it (CR-05).

**US-18** As a user, I want 7-day and 30-day trends, so that I see whether I'm improving.
- From the summary panel I can open 7-day and 30-day views of daily time per category and goals met per day.
- Days before install show as empty, not as zero.

### Admin & config — F-09, F-10, F-11

**US-19** As the admin, I want to change settings from a web console, so that I don't ship an app update for every tweak.
- Given I'm logged in, when I change a value and publish, then the new config is served to every app.
- Values with the wrong type, outside the allowed range, or missing are rejected with a clear message before publishing.
- Apps pick up the new config on next start or within 6 hours (A-17).

**US-20** As the admin, I want to manage pets, personalities, line pools and category defaults, so that content changes without an app update.
- I can add, edit and disable pets, personalities and lines, and set default categories for known apps.

**US-21** As a user, I want the app to keep working even if the server or the config is broken, so that the pet never disappears because of a backend problem.
- If config can't be fetched or fails validation on the phone, the app uses the last good config, otherwise the built-in defaults.
- A bad config never crashes the app.

**US-22** As a user, I want my own preferences to stick, so that the admin's defaults don't overwrite my choices.
- Settings marked user-editable can be changed within the admin's range; if the admin later narrows a range, my value is clamped into it.
- Admin-locked settings are shown read-only.

**US-23** As the admin, I want the console to require login, so that only I can change what every user sees.
- The console requires login, uses HTTPS, and rate-limits failed attempts.

### Stats — F-14

**US-24** As a user, I want to choose whether to share anonymous stats, so that I control my data.
- Setup asks once, default off; I can change it in Settings at any time.
- When opted in, the app sends only daily aggregates (A-09). No app names, no personal information.
- When I opt out, sending stops immediately.

**US-25** As the admin, I want to see aggregated stats, so that I can measure the success metrics.
- The console shows the metrics in section 2 over time, as aggregates only.

### Added in v1.1 — change requests CR-01 to CR-04

**US-26** (F-21, CR-01) As a user, I want to be told if the pet loses a permission it needs, so that it doesn't silently stop working.
- Given a required permission is revoked, when the service next checks, then a notification explains what stopped working, and tapping it opens the matching permission step.

**US-27** (F-20, CR-02) As a user, I want the pet to say something now and then when nothing is wrong, so that it feels alive.
- Given no bubble has shown recently, about every 30 min of screen time (config) the pet says an idle line that matches its mood.
- I can turn idle lines off in Settings; the admin can turn them off for everyone.
- Idle lines never show during a focus session.

**US-28** (F-18, CR-03) As a user, I want the app icon to open a home screen, so that I can see my day and reach focus, trends and settings.
- Given setup is done, when I open the app from the launcher, then I see today's totals, goal progress, the pet's mood, and buttons for Focus, Trends and Settings.

**US-29** (F-19, CR-04) As a user, I want to hide the pet for a while, so that it doesn't get in the way of a game or a call.
- When I tap Hide in the summary panel, the pet disappears for the hide duration (default 30 min, admin-configurable).
- While the pet is hidden, nags and break reminders arrive as notifications.
- I can bring the pet back early from the persistent notification or from Home; otherwise it returns when the time is up.

### Added in v1.3 — change requests CR-09, CR-10

**US-30** (F-06, CR-09, CR-10) As a user, I want to see how long I've been on my screen without a break, whatever I'm doing, and choose how firmly Nyx reacts, so that long sessions don't slip by.
- Given the screen is on, the pet's ring shows continuous screen use: time since my last break (screen off ≥ 5 min), in any app (A-31).
- In Settings I can set the continuous-use limit within the admin's range (the break trigger, US-11) and pick how Nyx reacts when it's reached: gentle (bubble + notification, the default) or obstructive.
- Obstructive is off unless I choose it, and the admin can turn the option off for everyone. Obstructive covers the current app with a break screen ("Take a break" / "5 more minutes", up to 3 times) that I can always dismiss after a short delay; it doesn't show during focus, while the pet is hidden, or during a call (D-88, D-89).

---

## 6. Non-functional requirements

**Performance**
- Limit and nag reactions: ≤ 60 s after a threshold is crossed.
- Summary panel opens in ≤ 1 s.
- Battery: the app's own use ≤ 3% of a full charge per day (A-19; measured during hardening).

**Scale**
- Backend: config reads from up to 10,000 installs (A-18); stats ingest of one request per opted-in device per day.
- On-device: at least 31 days of daily history.

**Availability**
- The app works fully offline and with the backend down (built-in or last good config).
- Backend: best effort for v1, no SLA.

**Security**
- Admin console: login required, HTTPS only, password stored with a slow hash, login rate-limited.
- The served config is public-read and never contains secrets (A-10).
- Stats endpoint accepts only the documented schema and is rate-limited per install ID.
- No API keys inside the app.

**Privacy & retention**
- Usage data stays on the device, except opt-in aggregates (A-09).
- On-device daily totals kept for at least 31 days; older data deleted (A-13). Uninstalling deletes everything (A-04).
- Server stats kept for 12 months (A-13).
- Privacy policy published before Play submission; the Play Data safety form matches it.

**Platforms**
- Android phones; minSdk 30 / Android 11 (A-07); targetSdk 36, required by Google Play for new apps.
- Portrait-first; tablets work but aren't optimised (A-12).
- Admin console: current desktop Chrome, Edge, Firefox.

**Offline / poor network**
- Every on-device feature works offline; config refresh and stats upload retry later.

**Localisation**
- English only (A-05).
- Device time zone and 12/24 h setting; the day resets at the local reset hour (A-08); changing time zones doesn't double-count a day.

**Accessibility**
- Alerts never rely on colour alone (glow + speech bubble + optional notification).
- Setup, Settings, summary and trends work with TalkBack and large system text.
- The pet's touch target is at least 48 dp.

---

## 7. Constraints

- Solo developer; collaboration mode: delivery.
- Target date 2026-10-25: a working build on the developer's phone and emulator (D-23). Backend, console, trends and stats follow in week 2, then Play submission.
- Google Play: personal account created before 13 Nov 2023, so no 14-day closed test is needed (Q-02). New-app review can still take several days (R-08).
- Play declarations needed: always-on foreground service type, Data safety, Health apps declaration (no health features), target audience 18+.
- Hosting budget: no constraint.
- Tech stack: decided in Phase 3.

---

## 8. Out of scope (v1)

- iOS
- Chat with the pet (post-v1, cloud LLM)
- Blocking or delaying apps (the opt-in obstructive break screen, CR-10, gets in the way but can always be dismissed, D-88)
- Detecting what's inside an app (e.g. Reels vs a lecture); no Accessibility Service
- User accounts, login, cloud backup, multi-device sync
- ADHD or any health positioning or claims
- Languages other than English
- Custom pet art beyond emoji
- Users under 18

---

## 9. Glossary

| Term | Meaning |
|---|---|
| Pet | The on-screen character; v1 renders it as an emoji face — Cat or Blob (D-22) |
| Personality | A preset voice for the pet's lines (e.g. cheerful coach, sarcastic buddy) |
| Line pool | The set of lines a personality can say, per situation |
| Mood | The pet's state, a number that rises and falls with the user's habits |
| Low point | Mood at or below the low threshold (sick/sad) or at zero (runs away) |
| Category | One of: Productive, Social & scrolling, Games, Entertainment, Neutral |
| Doomscrolling | Time spent in Social & scrolling apps (A-15) |
| Daily limit | Maximum time per day, per category or in total |
| Continuous stretch | Use of limited categories with no break in between; only a break resets it |
| Break | Screen off for at least 5 minutes (admin-configurable) |
| Nag | The pet's warning: glow → speech bubble → repeat every N minutes |
| Snooze | Pausing the one nag it was pressed on for a set time; limited per day; lowers mood |
| Idle line | Something the pet says when nothing is wrong |
| Hide | Temporarily removing the pet from the screen (default 30 min); nags arrive as notifications meanwhile |
| Break reminder | A prompt to step away, shown as speech bubble + notification |
| Focus session | A user-started session for a hard task, with a goal and allowed apps |
| Built-in defaults | Config values that ship inside the app |
| Admin config | Values published from the web admin console |
| User preference | A user's own value for an admin-allowed setting, within range |
| Day reset | The local time when daily totals and mood reset (default midnight) |
