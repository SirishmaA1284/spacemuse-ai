# API Specification (v1)

Base path: `/api/v1`. All routes return `application/json`.

## Implemented

### `POST /rooms/analyze`
Body: `{ imageBase64?: string, note?: string }`. When `imageBase64` is
present and `GEMINI_API_KEY` is configured, calls Gemini vision and returns
a `RoomAnalysis` (`source: "gemini"`). Otherwise — no image, no key, or a
malformed model response — returns a deterministic placeholder
`RoomAnalysis` (`source: "demo"`) so the endpoint always produces a usable
result. AR measurement data is not yet accepted (all measurements are
`ESTIMATED`, never `MEASURED` — see `docs/ai/limitations.md`).

### `POST /designs/:id/modify`
Body: `{ message: string }`. Runs the message through the Coordinator →
Intent Agent and returns the classified `IntentResult`. Does not yet mutate
design state (no execution agents implemented yet) — returns the intent plus
a `status: "recognized_not_yet_actionable"` marker for anything other than
`ASK_QUESTION`.

### `GET /health`
Liveness check. Returns `{ status: "ok", geminiConfigured: boolean }`.

### `GET /products/search`
Query params: `q: string` (required), `category?: string`, `maxPrice?: number`
(rupees). Runs the query through every configured `ProductProvider`
(`backend/src/agents/shoppingAgent.ts` — currently just `GoogleShoppingProvider`,
which needs `SERPAPI_KEY`). Returns
`{ results: ProductResult[], providersConfigured: boolean }` —
`providersConfigured: false` means no provider has credentials at all
(distinct from a configured provider finding zero matches), so the client
can show "no shopping provider configured" instead of a misleading blank
result list.

## Specified, not yet implemented (route returns 501 with a clear message)

```
POST   /designs
GET    /designs/:id
POST   /designs/:id/visualize
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
