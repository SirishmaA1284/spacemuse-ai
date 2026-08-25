# Changelog

All notable changes to this project are documented here.
Format loosely follows [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

### Fixed (2026-08-25, cont'd 2)
- `SERPAPI_KEY` configured — `GET /products/search` now returns real
  `GoogleShoppingProvider` results instead of `providersConfigured: false`.
- `backend/src/agents/shoppingAgent.ts`: `searchProducts()` now takes an
  optional `providers` param (defaults to the real list) so tests can
  inject fake providers instead of depending on which real keys happen to
  be set — `shoppingAgent.test.ts` was rewritten around this and no longer
  makes a real network call.

### Added (2026-08-25, cont'd)
- `GET /products/search` now wired to a new Shopping Agent
  (`backend/src/agents/shoppingAgent.ts`) over `GoogleShoppingProvider` —
  no longer a 501 stub. Returns `providersConfigured: false` (not a fake
  empty match) when no provider has credentials, per ADR-004's
  no-fabricated-data rule.
- Android: the room-scan result now has a "Shop" action per detected
  object that calls product search and shows results (or an honest
  "no shopping provider configured" message) in a new overlay; tapping a
  result opens it in the browser.
- Android: every scan state (scanning, error) now has a Cancel/Back action,
  and the camera screen's back button is rendered on top of every overlay
  instead of being covered by it — previously there was no way out of the
  scanning state once started.
- Fixed `.github/workflows/android-build.yml`: its push trigger was
  `branches: [main]`, but this repo's only branch is `master`, so the
  workflow likely never auto-ran on any push before this.

### Added (2026-08-25)
- `POST /rooms/analyze` now calls real Gemini vision (`backend/src/agents/roomAnalysisAgent.ts`,
  `backend/src/ai/prompts/room-analysis/analyzeRoom.prompt.ts`) and falls
  back to a deterministic demo `RoomAnalysis` when no image/key is
  available or the model response doesn't validate — the route no longer
  returns 501.
- Android: the camera screen now captures a real photo (CameraX
  `ImageCapture`) on a new "Scan Room" button, sends it to the backend, and
  renders the result (object list, estimated measurements, a `DEMO DATA`
  badge when applicable) instead of leaving the scan flow silent after
  capture. Added a Retrofit + kotlinx.serialization network client in
  `core/network`.
- Android UI pass: full Material 3 color scheme (containers, surface
  variants) and typography, restyled Home/Camera screens, a vector adaptive
  launcher icon (previously the default Android icon).

### Added
- Initial repository foundation: full `docs/` structure (architecture,
  product, AI, API, database, security, testing, ADRs, development log,
  roadmap).
- Backend (Node.js + TypeScript + Express) skeleton: versioned API router,
  coordinator/intent agent scaffolding, tool-call registry with schema
  validation, Gemini client wrapper, product-provider abstraction with a
  SerpApi Google Shopping implementation, Prisma schema covering the full
  data model, unit tests for budget calculation and intent detection.
- Android (Kotlin + Jetpack Compose) project skeleton: module boundaries
  (app/core/ai/camera), navigation graph, Home and Camera screen stubs.
- `.env.example` documenting every external credential the system uses.
- 7 initial ADRs covering stack and architecture decisions.

### Known limitations
- Android project has not been Gradle-built or run — no JDK/Android SDK
  available in the environment this was scaffolded in.
- Gemini calls are wired but untested end-to-end (no API key configured in
  this environment).
- Gemma on-device inference, ARCore scanning, visualization/image editing,
  try-in-space, and budget optimization search are architected in docs but
  not yet implemented in code.
