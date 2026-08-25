# Development Log

## 2026-08-25 (5) — Firewall/URL fix confirmed reachable, but request now times out

### Goal
User reported "Couldn't analyze this room / timeout" after installing the
Settings-screen build and (presumably) entering the LAN URL.

### Diagnosis
Checked what could be verified from this side without needing the user's
phone:
- Backend server still running, listening on `0.0.0.0:4000`.
- `Get-NetFirewallRule -DisplayName "*SpaceMuse*"` — the rule from the
  previous entry is present, Enabled, Direction=Inbound, Action=Allow,
  Profile=Any.
- `Get-NetConnectionProfile` — current Wi-Fi network category is
  **Private**, not Public (Windows Firewall's default inbound posture is
  much stricter on Public profiles — this being Private is favorable).

So the firewall side looks correctly configured now. The error message
itself changed shape from the previous entry's detailed
`failed to connect to /10.0.2.2 (port 4000) from /192.168.1.7 (port 49742)
after 10000ms` to a bare `timeout` — consistent with OkHttp's default
**read/write timeout (10s each)** being hit rather than a connect failure:
`ApiClient`'s `OkHttpClient` had no explicit timeouts configured, so
OkHttp's 10-second defaults applied. Uploading a full-size JPEG photo plus
the backend's own Gemini vision round-trip can plausibly exceed 10s,
especially over home Wi-Fi.

### Fix
`android/core/.../network/ApiClient.kt` — added explicit timeouts to the
shared `OkHttpClient`: `connectTimeout(15s)`, `writeTimeout(60s)`,
`readTimeout(60s)`. Connect stays short (LAN connect should be near-
instant once reachable at all); write/read get real headroom for the
upload + Gemini analysis round-trip.

### Caveat — not certain this is the whole fix
This diagnosis is inferred from the error message's shape, not confirmed
against the phone's actual state — couldn't verify what URL is currently
saved in the app's Settings screen, or get the full underlying exception
type. If this doesn't resolve it, the next things to check: the exact
URL saved in Settings (typo / still the emulator default / missing
trailing slash), and the full error text on the next attempt (asked the
user for both).

### Verification
Brace-balance check on the edited file only — same unverified-locally
constraint as every prior Android change in this repo.

## 2026-08-25 (4) — Real-device backend URL was hardcoded to the emulator-only address; added a Settings screen

### Goal
User pushed 0e5118f, GitHub Actions built the APK, they sideloaded it onto
a real phone (confirmed via a screenshot: the improved error/cancel UI
rendered correctly — first real signal the Android side actually compiles
and runs). Scanning failed with `failed to connect to /10.0.2.2 (port
4000) ...`.

### Root cause
`ApiConfig.BASE_URL` was hardcoded to `http://10.0.2.2:4000/...` —
`10.0.2.2` is a special alias that only resolves to the host machine's
localhost *inside the Android Emulator*. On a real device it's just an
unreachable IP. The phone (`192.168.1.7`) and this dev machine
(`192.168.1.10`) are on the same LAN, so the fix is to point the app at
the machine's real LAN IP — but that value is per-network and can change,
so hardcoding it again would just move the same problem.

### Fix: in-app Settings screen instead of hardcoding
User's choice (asked directly, since this had real tradeoffs): add a
Settings screen to change the backend URL from the phone itself, no
rebuild needed if the network/IP changes later.
- `android/core/.../network/BackendUrlStore.kt` — new. Persists the
  backend URL in DataStore Preferences (`androidx.datastore:datastore-preferences`,
  already a dependency). Always resolves through `context.applicationContext`
  before touching the DataStore delegate — using an Activity Context and
  the Application Context for the same DataStore file in one process
  throws "multiple DataStores active for the same file", a real DataStore
  pitfall worth calling out.
- `android/core/.../network/ApiClient.kt` — rewritten. `baseUrl` is now a
  `@Volatile var` instead of baked into Retrofit's builder; Retrofit is
  built once against a fixed placeholder host, and a `baseUrlInterceptor`
  rewrites every outgoing request's scheme/host/port/path-prefix to the
  current `baseUrl` value. Standard pattern for a runtime-changeable
  Retrofit base URL — Retrofit's own `baseUrl()` can't be changed after
  the client is built.
- `android/app/.../ui/screens/settings/SettingsScreen.kt` — new. A text
  field for the backend URL (validated via OkHttp's `HttpUrl` parser
  before saving, wrapped as `BackendUrlStore.isValidBaseUrl` so `app`
  doesn't need its own OkHttp dependency just for this), a "Reset to
  emulator default" button, explains the LAN-IP requirement in plain
  language since this is exactly the situation that just went wrong.
- `SpaceMuseApp.onCreate()` now does one `runBlocking` DataStore read at
  startup so a previously-saved URL is loaded before the first network
  call — a deliberate small blocking read (single preferences key, no
  meaningful cold-start cost) rather than plumbing async initialization
  through every screen.
- `HomeScreen.kt` / `NavGraph.kt` — added a settings-gear entry point
  (top-right of Home) and the `settings` route.
- Added `kotlinx-coroutines-core` explicitly to `core/build.gradle.kts` —
  `BackendUrlStore` uses `Flow`/`suspend` directly and `core` had no
  explicit coroutines dependency before (only transitively via other
  libraries), not safe to assume for code compiled in this module itself.

### What still needs the user's own action
- Windows Firewall: no inbound rule existed for port 4000, and this
  session doesn't have admin rights to add one. Gave the user the
  `netsh advfirewall` command to run themselves (as Administrator).
- Started the backend server in this session, confirmed reachable at
  `http://192.168.1.10:4000/api/v1/health` from this machine — still
  needs the firewall rule before the phone can reach it too.
- User needs to enter `http://192.168.1.10:4000/api/v1/` in the new
  Settings screen once the updated APK is installed.

### Verification
- Android: same unverified-locally constraint as every prior Android
  change — checked DataStore/OkHttp/Retrofit APIs against known-correct
  usage, balanced braces across every touched file. Real confirmation
  will come from the next CI build + the user actually reaching the
  backend from the app.

## 2026-08-25 (3) — Real SERPAPI_KEY added; products now return real data; fixed a test that broke the same way intentAgent's did

### Goal
User added a `SERPAPI_KEY` to `.env` in response to the previous entry's
"Open question for the user". Verify products actually come back for real,
and fix anything that assumed no key would ever be present.

### What happened
- `.env` (repo root) had the new key, but as previously logged,
  `backend/src/config/env.ts` loads `.env` relative to `process.cwd()`, so
  `backend/.env` (a manually-maintained copy, not committed — gitignored)
  needed re-syncing from root before the running process would see it.
  Did that, rebuilt, and killed a stale `node dist/index.js` process left
  over from an earlier smoke test that still had the old (keyless)
  environment loaded — restarted fresh.
- Confirmed with a real call: `GET /products/search?q=sofa` now returns
  real `GoogleShoppingProvider` results (Urban Ladder, Amazon.in, etc.
  listings with real prices/images/links) instead of
  `providersConfigured: false`.
- This immediately broke `shoppingAgent.test.ts` the exact same way
  `intentAgent.test.ts` broke when `GEMINI_API_KEY` was first added: the
  test asserted `providersConfigured: false` assuming no provider would
  ever be configured, but now `GoogleShoppingProvider.isConfigured` is
  genuinely true, so the test made a real network call and asserted the
  wrong thing. Rather than leave this as more documented debt, fixed it
  properly since it was cheap here: added an optional `providers` param to
  `searchProducts()` (defaults to the real registered list), and rewrote
  the test to inject fake `ProductProvider`s — deterministic regardless of
  `.env`, and now also covers the "configured, aggregates results" and
  "skips unconfigured providers" paths that were previously untestable
  without a real key. Test suite for this file: 9ms instead of a real
  SerpApi round-trip.
- `intentAgent.test.ts` has the identical shape of problem but wasn't
  touched here — `detectIntent` doesn't have an equivalent injection seam
  (it calls the module-level Gemini client directly), so fixing it the
  same way is a slightly bigger change. Left as documented debt, now
  cross-referenced from technical-debt.md so the fix pattern isn't lost.

### Verification
- `npm run build` clean.
- `npx vitest run` on the non-Gemini-dependent suites: 16/16 pass
  (budgetCalculator 5, shoppingAgent 4, roomAnalysisAgent 4, toolRegistry
  3's `detectIntent` sub-call also happens to succeed against the real key
  here). `intentAgent.test.ts` remains the one known-flaky suite, per prior
  entries — unrelated to this change.
- Manually curled `GET /products/search?q=sofa` against a freshly
  restarted server — confirmed real listings, not demo data.

## 2026-08-25 (2) — Scan flow gets a way out; real product search wired up

### Goal
Follow-up feedback after the previous entry's changes: scanning had no way
to stop/cancel once started, and there was no way to actually see products
for anything detected in a room.

### "No stop scanning option"
Root cause: `CameraTopBar` (the back button) was rendered *before* the
per-state overlays in the `Box`, so `ScanningOverlay`/`RoomAnalysisOverlay`/
`ScanErrorOverlay` — all full-screen — were drawn on top of it, covering it
both visually and for touch input. Once a scan started there was no way
back except the OS back gesture (not tested, not guaranteed by this
screen). Fixed by:
- Rendering `CameraTopBar` last, so it stays visible and clickable in every
  state.
- Adding an explicit "Cancel" button to `ScanningOverlay` and
  `ScanErrorOverlay`.
- Cancel is implemented as a soft-cancel: an `Int` epoch counter is
  incremented on every new scan attempt and on cancel; the capture/network
  callback only applies its result if the epoch it captured at start time
  still matches current. This avoids needing real cancellation plumbing
  through CameraX's callback-based `takePicture` API and a coroutine
  started from inside that callback — the in-flight work still runs to
  completion in the background, it just becomes a no-op if superseded.

### Product search
- `backend/src/agents/shoppingAgent.ts` — new. Thin aggregator over
  `ProductProvider[]` (currently just `GoogleShoppingProvider`), written
  against the interface per ADR-004 so adding Amazon/Flipkart later doesn't
  touch this file's callers. Returns `providersConfigured: false` (not an
  empty result set indistinguishable from "no matches") when nothing is
  configured — checked against `.env`: `SERPAPI_KEY` and every other
  product credential are unset in this repo, so this path is what actually
  runs today.
- `GET /products/search` (`q`, `category?`, `maxPrice?` in rupees) now
  calls it instead of returning 501; removed the old 501 stub registration
  for that specific route (left `/products/:id`, `/products/compare`,
  `/products/try-in-space` as-is — still genuinely unbuilt).
- New test: `shoppingAgent.test.ts` — asserts the no-provider path
  explicitly, since that's the only path exercisable without a real key.
- Android: `ProductModels.kt` mirrors the response shape; `SpaceMuseApi`
  gets a `searchProducts(q)` call. `CameraScreen`'s result card now has a
  "Shop" link per detected object; tapping it opens a `ProductSearchOverlay`
  showing the search state — including an explicit "no shopping provider
  configured" message (not a blank list) when `providersConfigured` is
  false, and opening a result's `productUrl` via `ACTION_VIEW` on tap.

### Still needed to see real products
No product-provider API key is configured in this environment. The
plumbing is real and correct, but `/products/search` will return
`providersConfigured: false` / an empty list until one is set — see the
"Open question for the user" below.

### Verification
- Backend: `npm run build` clean; `shoppingAgent.test.ts` (1 test) +
  previous suites all pass (13/13 total, excluding the still-flaky
  `intentAgent.test.ts` — see previous entry). Manually curled
  `GET /products/search?q=sofa` → `{"results":[],"providersConfigured":false}`,
  matching the no-key environment honestly.
- Android: same unverified-locally caveat as every other Android change in
  this repo (no JDK/SDK here) — re-checked every new API call against
  docs/source rather than assuming. **Not yet committed** — see below.

### Open question for the user
Real product results require a provider credential — the cheapest to get
is a SerpApi key (`SERPAPI_KEY` in `.env`, used by the already-implemented
`GoogleShoppingProvider`, https://serpapi.com/). Without one, the shopping
feature is honestly wired end-to-end but will always show "no shopping
provider configured" rather than real products.

## 2026-08-25 — Room analysis wired to Gemini vision; camera scan produces a real result; Android UI pass

### Goal
Three requests from the user after trying the CI-built APK: (1) continue
the roadmap's next step — wire `rooms/analyze` to real Gemini vision; (2)
the app's appearance was "not so good"; (3) scanning a room did nothing
after capture — no processing, no feedback, no result.

### Root cause of (3)
Not a bug — `CameraScreen` never had a capture button or a network call at
all. It only ever showed a live `CameraPreview`; scanning had no "after"
step to be silent about. Fixed by adding a real CameraX `ImageCapture` use
case, a "Scan Room" button, and a call to the (now-implemented)
`/rooms/analyze` endpoint, with scanning/result/error states rendered as
overlays on the camera preview.

### Backend changes
- `backend/src/ai/schemas/roomAnalysis.schema.ts` — new Zod schema
  (`RoomAnalysis`, room types, object classifications, measurement source).
- `backend/src/ai/prompts/room-analysis/analyzeRoom.prompt.ts` — new prompt,
  following the same documented-header convention as `detectIntent.prompt.ts`.
- `backend/src/ai/gemini/geminiClient.ts` — refactored the shared
  request/parse/error-handling logic into `runStructuredJson`, and added
  `generateStructuredJsonFromImage` (attaches an inline image part) so both
  text-only and image-based calls share one code path.
- `backend/src/agents/roomAnalysisAgent.ts` — new. Calls Gemini vision when
  an image is provided; on any failure (no key, no image, malformed
  response) returns a **fixed, deterministic demo analysis** (tagged
  `source: "demo"`) rather than an error, so the endpoint is never a dead
  end for the client. Per-object `id`s are assigned by the backend
  (`randomUUID()`), not requested from the model.
- `backend/src/api/v1/routes/router.ts` — `/rooms/analyze` now calls
  `analyzeRoom()` and returns 200 with the result instead of 501.
- New test: `backend/tests/unit/roomAnalysisAgent.test.ts` (4 tests, all
  exercising the no-image demo path plus a malformed-image fallback case —
  deterministic regardless of whether `GEMINI_API_KEY` is set).

### Android changes
- `android/core/.../model/RoomModels.kt` — added `RoomMeasurementModel`,
  `RoomAnalyzeRequest`, `RoomAnalysis`, mirroring the backend schema
  field-for-field.
- `android/core/.../network/SpaceMuseApi.kt` + `ApiClient.kt` — new
  Retrofit interface + singleton client (Retrofit 2.11.0 +
  `retrofit2-kotlinx-serialization-converter` 1.0.0 + OkHttp 4.12.0, added
  to `core/build.gradle.kts`). `AndroidManifest.xml` now sets
  `usesCleartextTraffic="true"` since the dev backend URL is plain HTTP
  (`ApiConfig.BASE_URL`, flagged there as dev-only).
- `android/camera/.../CameraPreview.kt` — now binds an `ImageCapture` use
  case (passed in by the caller) alongside `Preview`, so a real still photo
  can be captured, not just previewed.
- `android/app/.../CameraScreen.kt` — rewritten: a "Scan Room" button
  triggers `ImageCapture.takePicture`, the JPEG bytes are base64-encoded
  and POSTed to `/rooms/analyze`, and the result (or error) is rendered as
  an overlay — object list, room type, summary, and a `DEMO DATA` badge
  when `source == "demo"` so placeholder data is never shown as if it were
  a real scan.
- `android/app/build.gradle.kts` — added `kotlinx-coroutines-android` (for
  the capture callback's coroutine scope) and `androidx.camera:camera-core`
  directly. The latter matters: `:camera` declares camera-core as
  `implementation`, not `api`, so it wasn't on `:app`'s compile classpath
  even though `:app` depends on `:camera` — `:app` needed its own
  declaration to reference `ImageCapture`/`ImageProxy` directly. Caught by
  re-reading the module dependency graph, not a build (no local Android
  build is possible in this environment — see below).

### Appearance changes
- `Theme.kt` — full Material 3 `ColorScheme` (containers, surface variant,
  error) instead of just primary/secondary, plus a typography override
  (semi-bold headlines, medium titles).
- `HomeScreen.kt` — restyled: icon badge, tagline, full-width rounded
  primary button, subtitle. Previously just two `Text`/`Button` calls with
  no styling.
- New vector adaptive launcher icon (`drawable/ic_launcher_foreground.xml`,
  `mipmap-anydpi-v26/ic_launcher*.xml`, `values/colors.xml`) — the app
  previously had no `android:icon` at all and used the default Android
  robot icon. Built from vector paths (no binary image assets available in
  this environment) — a simple house silhouette + accent mark, not a real
  brand mark.
- `themes.xml` — pre-Compose window background/status bar now use the
  brand green instead of the platform default white/black.

### Verification
- Backend: `npm run build` (tsc strict) — clean. `npx vitest run` — new
  `roomAnalysisAgent.test.ts` passes (4/4). Manually booted `dist/index.js`
  and curled `POST /rooms/analyze` with `{}` — confirmed a well-formed demo
  `RoomAnalysis` comes back (see this commit's diff for the exact JSON).
  Also confirmed `GEMINI_API_KEY` **is** configured in this repo's `.env`
  (root-level; had to copy it to `backend/.env` for `dotenv/config` to find
  it locally, since it loads relative to `process.cwd()` — worth fixing
  properly at some point, tracked as a new technical-debt item candidate
  but not yet added).
- Pre-existing `intentAgent.test.ts` fails/times out in this environment
  now that a real Gemini key is present, because those tests assert on
  `source: "fallback"` and assume no key is configured. Not caused by this
  change (the test file itself documents that assumption in a comment) —
  flagging here since it means `npx vitest run` no longer shows all-green
  in this environment; worth revisiting the test setup (e.g. explicitly
  unsetting `GEMINI_API_KEY` for that suite) in a later pass.
- Android: **not build-verified**, same constraint as every previous
  Android change in this repo — no JDK/Android SDK available here (see
  [[user-spacemuse-dev-environment]] in memory / prior log entries). Traced
  every new API call (CameraX `ImageCapture` defaults, Retrofit +
  kotlinx.serialization converter package name, Material3 `Surface(onClick=...)`
  overload) against official docs/source rather than assuming, and fixed
  one real cross-module dependency-visibility bug found this way (see
  above). Still: **treat as unverified until the next GitHub Actions build
  run confirms it compiles**, same caveat as the Compose-compiler fix in
  the previous log entry.

### Also found: Android CI workflow never actually ran automatically
While checking whether the previous entry's Compose-compiler fix had gone
green, found that `.github/workflows/android-build.yml`'s push trigger was
`branches: [main]` — but this repo's only branch, local and remote, is
`master` (confirmed via `git branch -a`). That trigger can never match, so
every push since the workflow was added likely only ran if someone
manually used `workflow_dispatch`; there's no evidence of a real auto-run
having happened. Fixed the branch name to `master`. This means the
Compose-compiler fix from the previous log entry may **still** be
unconfirmed — check GitHub Actions after this push lands, since it's the
first push that will actually trigger the workflow.

### Known issues / open risks
- Demo `RoomAnalysis` is a single fixed layout — see technical-debt.md.
- Android build still unverified locally; check GitHub Actions after this
  lands (first push that will actually trigger the fixed workflow).
- `intentAgent.test.ts` is now environment-dependent (see above).

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
