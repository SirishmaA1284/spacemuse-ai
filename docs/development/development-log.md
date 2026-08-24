# Development Log

## 2026-08-24 (2) — First real Android CI build, fix Compose compiler config

### Goal
Get the Android module actually compiling somewhere, since the scaffolding
environment had no JDK/Android SDK to verify it locally. Added a GitHub
Actions workflow (`.github/workflows/android-build.yml`) that builds the
debug APK on GitHub's runners and uploads it as a downloadable artifact —
chosen because the user's local machine doesn't meet Android Studio's
system requirements either.

### Problem found (first real CI run)
Build failed immediately at project configuration:

```
A problem occurred configuring project ':app'.
> Starting in Kotlin 2.0, the Compose Compiler Gradle plugin is required
  when compose is enabled.
```

Root cause: as of Kotlin 2.0, the Jetpack Compose compiler is no longer
bundled with the Kotlin Gradle plugin — it ships as a separate Gradle
plugin (`org.jetbrains.kotlin.plugin.compose`) that must be applied
explicitly on every module with `buildFeatures.compose = true`. The
original scaffold used the pre-2.0 `composeOptions.kotlinCompilerExtensionVersion`
mechanism, which no longer applies with Kotlin 2.0.20 (declared in
`android/build.gradle.kts`). This wasn't caught before because static
review of Gradle Kotlin DSL files doesn't execute Gradle's project
configuration phase — only an actual build does.

### Fix
- `android/build.gradle.kts`: declared
  `id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false`.
- `android/app/build.gradle.kts` and `android/camera/build.gradle.kts` (the
  two modules with Compose enabled): applied that plugin and removed the
  now-unused `composeOptions { kotlinCompilerExtensionVersion = ... }`
  block.

### Status
Fix pushed; next CI run will confirm whether this was the only
configuration issue or whether further errors surface once configuration
succeeds and actual compilation starts. Updating this log again once a run
is confirmed green.

## 2026-08-24 — Foundation (Phase 1) + Intent Detection (Phase 3)

### Goal
Scaffold the full repository per the product spec: documentation, backend,
and Android client, in dependency-aware order, without fabricating any
functionality or claiming unverified things work.

### Environment inspected
- OS: Windows 11, PowerShell + Git Bash both available
- Node.js v22.16.0, npm 11.17.0 — present, used for backend
- Java/JDK: not found (`java` not on PATH)
- Gradle: not found on PATH
- Git: 2.41.0 — present, repo initialized (`git init`)
- Python 3.11.4 present, not used (backend chosen as Node/TS — see ADR-007)

Consequence: the Android project could be scaffolded but **not
Gradle-built**, since no JDK/Android SDK is available in this environment.
The backend, running on Node, could be fully installed, built, and tested.

### Changes
- Created full `docs/` tree (architecture, product, ai, api, database,
  security, testing, decisions/ADRs, development) — see `CHANGELOG.md` for
  the summary list; individual files listed via `git status` in this
  commit.
- Created `backend/`: Express + TypeScript API, Prisma schema (17 models
  covering the full section-55 entity list), Gemini client wrapper
  (`@google/genai`), intent-detection agent (Gemini-backed with a
  rule-based fallback), a Zod-validated tool registry, a real product
  provider (`GoogleShoppingProvider` via SerpApi), and 3 unit test files.
- Created `android/`: Gradle Kotlin DSL project, 4 modules (`app`, `core`,
  `ai`, `camera`), Compose UI (Home + Camera screens), CameraX live preview
  wired with runtime permission handling, Hilt DI setup, Gemma interface
  stub (no inference implementation yet).

### Problems encountered & fixes
1. **npm install left `@prisma/client`, `prisma`, and `esbuild` postinstall
   scripts unapproved** (npm's `allowScripts` gate) — Prisma client
   generation would have silently not run. Fixed by approving those 5
   packages explicitly (`npm approve-scripts`) and running `npm rebuild` +
   `npx prisma generate`.
2. **Real bug in the intent fallback classifier**: "Redesign the entire
   room in a modern style." was misclassified as `CHANGE_STYLE` instead of
   `FULL_REDESIGN`, because the `CHANGE_STYLE` keyword rule (`style|theme|
   ...`) was checked before the `FULL_REDESIGN` rule, and the sentence
   contains both a redesign phrase and the word "style". Root cause: rule
   list ordering didn't account for phrases matching multiple keyword sets.
   Fix: moved the `FULL_REDESIGN` pattern to the front of the rule list in
   `backend/src/agents/intentAgent.ts` with a comment explaining why order
   matters here. Caught by the unit test suite, not manual inspection.

### Tests
`cd backend && npm install && npx prisma generate && npm run build && npx vitest run`

```
✓ tests/unit/budgetCalculator.test.ts (5 tests)
✓ tests/unit/toolRegistry.test.ts (3 tests)
✓ tests/unit/intentAgent.test.ts (6 tests)

Test Files  3 passed (3)
     Tests  14 passed (14)
```

`npm run build` (tsc, strict mode) — completed with zero errors.

Runtime smoke test: booted `dist/index.js` on a local port and confirmed
via `curl`:
- `GET /api/v1/health` → `{"status":"ok","geminiConfigured":false}`
- `POST /api/v1/designs/:id/modify` with a rearrangement message → correctly
  classified `REARRANGE` via the fallback path (no `GEMINI_API_KEY` set in
  this environment) and returned the honest
  `"recognized_not_yet_actionable"` status.
- `GET /api/v1/products/search` → `501` with a `not_implemented` body, as
  documented.

### Results
Backend: installs, builds, and passes all tests in this environment.
Android: written but **not build-verified** — no JDK/Android SDK available
here. Must be opened in Android Studio to confirm it compiles; treat as an
open risk until that happens (tracked below).

### Known issues / open risks
- Android Gradle build is unverified — could have compile errors not caught
  by static review (dependency version mismatches, missing imports).
- No `gradlew`/Gradle wrapper jar checked in (can't generate a binary wrapper
  jar without Gradle installed) — first open in Android Studio must
  generate it.
- Gemini calls are implemented but never exercised against the real API in
  this environment (no key configured) — only the fallback path is
  verified.
- No auth, no rate limiting, no CI — all explicitly out of scope for this
  pass, tracked in `docs/development/technical-debt.md`.

### Next steps
See `docs/development/roadmap.md` "Immediate next steps" — Room
Understanding (Gemini vision wiring) is the next piece, since Rearrangement
Mode and every downstream agent depend on having a real `RoomAnalysis` to
work from.
