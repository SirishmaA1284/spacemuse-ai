# Changelog

All notable changes to this project are documented here.
Format loosely follows [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

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
