# shared/

The contract between the Android app, the backend and the admin console. **No business logic lives here** — only data shapes, the default data and test files.

## What's here

| File | What it is | Used by |
|---|---|---|
| `config.schema.json` | JSON Schema (draft-07) for the one config document (M2) | Backend (validates drafts), admin console (generates the form) |
| `default-config.json` | The built-in defaults (M2, D-43) | Copied into the app's assets at build time; seeded as the server's first published config |
| `fixtures/valid/*.json` | Configs that must be **accepted** | Phone tests (Kotlin validator) and backend tests (schema + cross-field rules) |
| `fixtures/invalid/*.json` | Configs that must be **rejected**, one broken rule per file; the file name says which rule | Same tests |

## Rules

- Changing the schema means updating `default-config.json`, adding or updating fixtures, and keeping the Kotlin validator in sync — in the same change.
- The phone and the backend must accept and reject exactly the same fixture files (D-39). CI enforces this.
- See `docs/ARCHITECTURE.md` §5 for how the config document works.

## How to test

Fixtures are checked by each side's own test command:
- Android: `./gradlew test` (in `android/`), from M2.
- Backend: `npm test` (in `backend/`), from M10.
