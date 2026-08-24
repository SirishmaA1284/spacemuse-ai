# Technical Debt / Known Gaps

Tracked honestly so nothing here is silently forgotten.

- Auth is a pass-through stub — no real user identity yet, so `Design`/`Room`
  rows aren't actually scoped per-user in any enforced way.
- `DATABASE_URL` defaults to SQLite; Postgres migration needed before
  production (native enum support, concurrent write characteristics).
- Gemini client targets the Developer API, not Vertex AI (see ADR-003) —
  fine for now, revisit before production scale/billing needs.
- `rooms/analyze` and most `/api/v1` routes are documented 501 stubs — see
  `docs/api/api-specification.md` for the exact list.
- Android project is unverified — no JDK/Android SDK in the scaffolding
  environment, so `./gradlew build` has never been run against this code.
- No CI pipeline configured yet.
- No rate limiting / abuse protection on the backend.
- Gemma on-device module has no actual model loading/inference code yet.
