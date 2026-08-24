# AI Architecture

## Two-tier model strategy

| Tier | Model | Where | Used for |
|---|---|---|---|
| Cloud | Gemini (2.5 Pro / Flash) | Backend only | Multimodal room understanding, design reasoning, image generation/editing, complex product reasoning, Gemini Live voice |
| On-device | Gemma (via MediaPipe LLM Inference / AI Edge) | Android `ai/` module | Local preference reasoning, lightweight conversational context, local memory retrieval — no network required |

Full detail: `docs/architecture/gemini.md`, `docs/architecture/gemma.md`.

## Structured output discipline

All Gemini calls that are meant to affect design state request **structured
JSON output** validated against a Zod schema in `backend/src/ai/schemas/`
(e.g. `IntentResult`, `RoomAnalysis`, `DesignModification`). Free-text
responses are only used for conversational replies shown directly to the
user, never for state mutation. See `docs/api/tools.md`.

## Prompt management

Prompts live in `backend/src/ai/prompts/<domain>/`, one file per prompt, never
inlined in business logic. Each prompt file documents purpose, expected
inputs/outputs, and constraints as a header comment. See `docs/ai/prompts.md`
for the full catalog and `docs/ai/model-selection.md` for which model/tier
each prompt targets.

## Uncertainty communication

Any AI-derived measurement or claim that isn't sensor-verified is returned
with a `confidence` and `source: "estimated" | "measured"` field (see
`RoomObject` in `docs/database/schema.md`) and the UI must render estimated
values with a qualifier ("approximately"), never as exact figures. See
`docs/ai/limitations.md`.
