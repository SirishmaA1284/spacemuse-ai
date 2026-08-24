# Prompt Catalog

| Prompt | File | Purpose | Output schema |
|---|---|---|---|
| Intent detection | `backend/src/ai/prompts/intent/detectIntent.prompt.ts` | Classify a user message into one of 17 intents + extract entities/constraints | `IntentResult` (`backend/src/ai/schemas/intentResult.schema.ts`) |
| Room analysis | `backend/src/ai/prompts/room-analysis/analyzeRoom.prompt.ts` | (planned Phase 2) Structured room breakdown from image(s) | `RoomAnalysis` (planned) |

Each prompt file header documents: purpose, inputs, outputs, constraints,
version, evaluation notes — per spec section 79. Only one prompt
(`detectIntent`) is implemented and live-called in this pass; others are
listed here as the target catalog per `docs/development/roadmap.md`.

## Versioning convention

Prompt files carry a `PROMPT_VERSION` constant. Bump on any behavior-changing
edit; log the change in `docs/ai/evaluation.md` if you have before/after
samples.
