# Prompt Catalog

| Prompt | File | Purpose | Output schema |
|---|---|---|---|
| Intent detection | `backend/src/ai/prompts/intent/detectIntent.prompt.ts` | Classify a user message into one of 17 intents + extract entities/constraints | `IntentResult` (`backend/src/ai/schemas/intentResult.schema.ts`) |
| Room analysis | `backend/src/ai/prompts/room-analysis/analyzeRoom.prompt.ts` | Structured room breakdown from a single room photo | `RoomAnalysis` (`backend/src/ai/schemas/roomAnalysis.schema.ts`) |

Each prompt file header documents: purpose, inputs, outputs, constraints,
version, evaluation notes — per spec section 79. Both `detectIntent` and
`analyzeRoom` are implemented and live-called in this pass.

## Versioning convention

Prompt files carry a `PROMPT_VERSION` constant. Bump on any behavior-changing
edit; log the change in `docs/ai/evaluation.md` if you have before/after
samples.
