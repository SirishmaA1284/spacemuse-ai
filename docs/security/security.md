# Security

## Secrets

- All credentials live in backend `.env` (gitignored). `.env.example` tracks
  key names only, never values.
- Android app holds **no** cloud API keys — it calls the backend exclusively.
- `backend/src/config/env.ts` validates required env vars at boot and fails
  fast with a clear error rather than running with undefined behavior.

## Input validation

Every route handler validates its body against a Zod schema before it
reaches an agent. Every tool-registry entry validates its input the same way
before touching `DesignState` (see `docs/api/tools.md`).

## AuthN/AuthZ

Not yet implemented. `backend/src/security/authMiddleware.ts` is a
pass-through stub, clearly marked, wired to Firebase Auth in a later phase.
**Do not deploy this API publicly before that lands.**

## Least privilege

Product-provider API keys are scoped to search/read-only where the provider
supports scoped keys (e.g. Amazon PA API IAM policy) — document the actual
scope granted next to each provider's setup notes when configured.

## Logging

`backend/src/config/env.ts` and request logging middleware must never log
full request bodies containing images or API keys — log request id, route,
status, and duration only. See `docs/security/privacy.md` for image handling.
