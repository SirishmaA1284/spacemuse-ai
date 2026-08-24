# SpaceMuse AI

**AI-powered Multimodal Interior Design & Spatial Intelligence Agent**

SpaceMuse AI lets a user show their real living space to an AI (via smartphone
camera or uploaded images), understands the room and the user's actual intent,
and acts as a professional interior designer — preferring rearrangement of
existing objects over unnecessary replacement, recommending real products
within a stated budget, and visualizing changes before anything is purchased.

This repository is the foundation of a long-term product, not a hackathon
throwaway. See `docs/development/roadmap.md` for the full phased plan and
`docs/development/development-log.md` for what has actually been built so far
(these two files are the source of truth on project status — this README
describes the target architecture, not necessarily what is live in v0).

## Repository layout

```
android/    Kotlin + Jetpack Compose client (camera, AR, UI, on-device Gemma)
backend/    Node.js + TypeScript API (agent orchestration, Gemini, product
            search, persistence)
docs/       Architecture, product, AI, API, database, security, testing docs
            and Architecture Decision Records (ADRs)
scripts/    Setup and verification scripts
tests/      Cross-cutting / end-to-end test placeholders
```

## Current status (v0 — Foundation)

Implemented in this pass:
- Full documentation skeleton (architecture, product, AI, API, DB, security, testing, ADRs)
- Backend: Express + TypeScript API skeleton, agent/tool-call scaffolding,
  Prisma schema for the full data model, intent-detection agent wired to the
  Gemini API (falls back to a rule-based stub when no API key is configured),
  a real product-provider interface with one implemented provider (SerpApi
  Google Shopping) that is inert without a key, unit tests for
  budget math and intent routing.
- Android: Kotlin/Compose project skeleton (module boundaries per
  `docs/architecture/mobile-architecture.md`), navigation graph, Home and
  Camera screen stubs. **Not yet build-verified** — this machine has no JDK /
  Android SDK installed, so the Gradle build has not been run. See
  `docs/development/development-log.md` for exact verification status.

Not yet implemented (see roadmap): Gemini Live voice, on-device Gemma
inference, ARCore spatial scanning, visualization/image-editing pipeline,
try-in-space, budget optimizer search, design health scoring, multi-room /
whole-home budgeting.

## Getting started

### Backend
```bash
cd backend
npm install
cp ../.env.example ../.env   # fill in real keys — see docs/api/api-specification.md
npm run dev
```

### Android
Open `android/` in Android Studio (Hedgehog+). Requires JDK 17 and Android
SDK 34. Gradle sync will fetch dependencies on first open. This has not been
build-verified in this environment (no JDK/Android SDK present here) — treat
it as a reviewed-but-unverified skeleton until opened in Studio.

## Required external APIs

See `docs/api/api-specification.md` and `.env.example` for the full list and
exactly where each key is consumed. Short version: Gemini API key is required
for any real AI reasoning; product-search keys (SerpApi/Amazon/Flipkart) are
optional and gate real vs. demo product data; everything else is optional and
degrades gracefully when absent.
