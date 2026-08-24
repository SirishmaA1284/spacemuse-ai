# Contributing

## Workflow for every feature

1. Understand the requirement against `docs/product/product-requirements.md`.
2. Check `docs/architecture/` for the relevant subsystem before adding code.
3. If the change is architecturally significant, add an ADR under
   `docs/decisions/ADRs/`.
4. Implement, keeping module boundaries in `android/` and `backend/src/`
   intact — don't reach across layers.
5. Add/update tests next to the code (`backend/tests/`, Android
   `src/test`/`src/androidTest`).
6. Update `CHANGELOG.md`, `docs/development/development-log.md`, and
   `docs/development/roadmap.md` if scope or status changed.
7. Never mark a feature done in docs if it isn't build-verified — say so
   explicitly (see `docs/development/development-log.md` conventions).

## Secrets

Never commit `.env`. Only `.env.example` (placeholders) is tracked.

## Code style

- Backend: TypeScript strict mode, ESLint config in `backend/`.
- Android: Kotlin, Compose, standard Android Studio formatting.
