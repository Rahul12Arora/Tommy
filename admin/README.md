# admin/

The Nyx admin console (React + Vite, plain JavaScript). The developer logs in, edits the draft config in a form generated from `shared/config.schema.json`, sees what differs from the live version and publishes it to every phone. Design: `docs/ARCHITECTURE.md` §14.11 (screens W-01 to W-03).

It's static files, not a server: in production the backend image builds it and serves it at `/console/` (D-42), so there's no Dockerfile here.

## Screens

- **Login (W-01):** the backend's `ADMIN_USERNAME` and the password behind `ADMIN_PASSWORD_HASH` (how to make one: `backend/README.md` → Setup).
- **Settings (W-02)** and **Pets & lines (W-03):** two tabs over one draft document. **Save draft** checks the schema in the browser, then the server checks everything again, including the cross-field rules (its errors appear at the top and under the field). **Publish** is enabled once a saved draft has no unsaved edits. **Changes** lists every value that differs from the live version.

## Layout

```text
src/
├── features/
│   ├── login/     ← LoginPage.jsx (W-01)
│   └── config/    ← ConfigEditor.jsx (page), ConfigForm.jsx (RJSF form), sections.js (which keys go on which tab),
│                    configDiff.js, ChangesList.jsx, ServerErrors.jsx
├── generated/     ← configValidator.js, compiled from the schema; not committed
├── api.js         ← every call to the backend
├── App.jsx        ← login over the editor; no router
└── main.jsx
scripts/compile-validator.js   ← runs before `dev` and `build` (D-140)
```

A new section in the schema shows up on the Settings tab without a code change. Content sections are listed in `sections.js`.

## Run

Needs Node 26.12.1 and the backend on port 3000 (`docker compose up` from the repo root, or `npm run dev` in `backend/`).

```bash
npm ci
npm run dev        # http://localhost:5173/console/ — calls to /admin and /v1 are proxied to localhost:3000
```

Restart `npm run dev` after changing `shared/config.schema.json`: the validator is compiled at start.

To try the production build the way it ships: `docker compose up --build`, then open http://localhost:3000/console/.

## Test and lint

```bash
npm run lint       # Biome
npm run build      # production build into dist/ (also what CI runs)
```

There are no console unit tests (TECH_STACK §5): the rules live in the schema and the backend, which have their own tests. CI lints and builds the console on every push that touches `admin/` (`.github/workflows/backend.yml`, job `console`).
