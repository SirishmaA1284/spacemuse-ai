# Testing Strategy

## Backend

- **Unit** (`backend/tests/unit/`): pure logic — intent fallback classifier,
  budget math, Zod schema validation. Run with `npm test` (Vitest).
- **Integration** (planned): Gemini client against a mocked HTTP layer,
  Prisma against a real SQLite test DB.
- **E2E** (planned, `tests/e2e/`): full scan → intent → recommend flow once
  enough agents exist to make it meaningful.

## Android

- **Unit** (`src/test/`): ViewModel logic once ViewModels exist beyond stubs.
- **UI** (`src/androidTest/`): Compose UI tests for screens once flows exist.
- Not runnable in this environment (no JDK/Android SDK) — see
  `docs/development/development-log.md`.

## What's actually verified right now

Only backend unit tests (`docs/testing/results.md` has the latest run
output). Everything else above is the target structure, not a claim of
current coverage.
