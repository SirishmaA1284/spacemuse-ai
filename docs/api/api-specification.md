# API Specification (v1)

Base path: `/api/v1`. All routes return `application/json`.

## Implemented

### `POST /rooms/analyze`
Accepts a room image (base64 or multipart — see route handler) plus optional
AR measurement data. Returns a `RoomAnalysis` stub today (structure defined,
real Gemini vision call not yet wired — see `docs/development/technical-debt.md`).

### `POST /designs/:id/modify`
Body: `{ message: string }`. Runs the message through the Coordinator →
Intent Agent and returns the classified `IntentResult`. Does not yet mutate
design state (no execution agents implemented yet) — returns the intent plus
a `status: "recognized_not_yet_actionable"` marker for anything other than
`ASK_QUESTION`.

### `GET /health`
Liveness check. Returns `{ status: "ok", geminiConfigured: boolean }`.

## Specified, not yet implemented (route returns 501 with a clear message)

```
POST   /designs
GET    /designs/:id
POST   /designs/:id/visualize
GET    /products/search
GET    /products/:id
POST   /products/compare
POST   /products/try-in-space
GET    /preferences
PUT    /preferences
POST   /budget/optimize
GET    /design-health/:id
```

Each of these has a route file present under `backend/src/api/v1/routes/`
returning HTTP 501 with `{ error: "not_implemented", phase: "<roadmap phase>" }`
rather than a 404 — so the API surface documents the target contract even
before the logic exists, and clients can distinguish "doesn't exist" from
"not built yet."

## Auth

Not yet implemented — all routes are currently open. `backend/src/security/authMiddleware.ts`
is a documented stub (pass-through) to be wired to Firebase Auth in a later
phase. Do not treat this API as production-safe to expose publicly as-is.
