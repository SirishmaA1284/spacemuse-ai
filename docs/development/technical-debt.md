# Technical Debt / Known Gaps

Tracked honestly so nothing here is silently forgotten.

- Auth is a pass-through stub — no real user identity yet, so `Design`/`Room`
  rows aren't actually scoped per-user in any enforced way.
- `DATABASE_URL` defaults to SQLite; Postgres migration needed before
  production (native enum support, concurrent write characteristics).
- Gemini client targets the Developer API, not Vertex AI (see ADR-003) —
  fine for now, revisit before production scale/billing needs.
- Most `/api/v1` routes are documented 501 stubs — see
  `docs/api/api-specification.md` for the exact list. (`rooms/analyze` is
  implemented, with a demo fallback when no image/key is available.)
- `rooms/analyze`'s demo fallback (`backend/src/agents/roomAnalysisAgent.ts`)
  is a fixed, hand-written living-room layout — it never varies by input.
  Fine as a "the pipeline works end-to-end" demo, but not useful for
  actually testing UI behavior against different room shapes.
- No product provider credential is configured in this repo's `.env`
  (`SERPAPI_KEY` and everything else in the "Product Discovery" section are
  empty) — `GET /products/search` is fully wired but will always return
  `providersConfigured: false` until one is added.
- Android project is unverified — no JDK/Android SDK in the scaffolding
  environment, so `./gradlew build` has never been run against this code.
- `backend/src/config/env.ts` uses `dotenv/config`, which loads `.env`
  relative to `process.cwd()`. The repo's `.env` lives at the repo root, so
  running backend commands from `backend/` (as documented everywhere) never
  actually finds it — needs a `backend/.env` copy or an explicit `dotenv.config({ path: ... })`
  pointing at the root file.
- `backend/tests/unit/intentAgent.test.ts` assumes no `GEMINI_API_KEY` is
  configured (asserts `source: "fallback"`) — fails/times out in any
  environment where a real key is set, since `detectIntent` then genuinely
  calls Gemini. Should mock/unset the key for that suite explicitly instead
  of relying on the ambient environment.
- No CI pipeline configured yet.
- No rate limiting / abuse protection on the backend.
- Gemma on-device module has no actual model loading/inference code yet.
