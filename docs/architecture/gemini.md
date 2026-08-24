# Gemini — Cloud Intelligence

## Role

Gemini is the only component permitted to perform: multimodal (image+text)
room reasoning, complex design reasoning, image generation/editing for
visualization, and real-time voice via Gemini Live.

## Integration point

`backend/src/ai/gemini/geminiClient.ts` wraps the official
`@google/genai` SDK. Called only from agents (`backend/src/agents/`), never
directly from route handlers, so every call goes through the same
error-handling and logging path.

## Credential

`GEMINI_API_KEY` (backend `.env` only — see `.env.example`). Obtain from
[Google AI Studio](https://aistudio.google.com/apikey) for prototyping, or a
Vertex AI service account for production (different auth path — Vertex uses
`GOOGLE_CLOUD_PROJECT_ID` + ADC, not a raw API key). The client currently
targets the Gemini Developer API (API-key auth); switching to Vertex is a
documented follow-up, not yet implemented (see `docs/development/technical-debt.md`).

## Gemini Live

Not yet implemented (Phase 5 in `docs/development/roadmap.md`). Will use the
Live API's WebSocket session with function/tool calling wired to the same
`backend/src/tools/toolRegistry.ts` used by the synchronous path, so voice
and text share one source of truth for what mutations are allowed.

## Failure behavior

If `GEMINI_API_KEY` is unset or the API call fails, `geminiClient.ts` returns
a typed error result (`{ ok: false, reason }`) rather than throwing. Callers
(agents) are required to handle both branches — see
`backend/src/agents/intentAgent.ts` for the reference pattern (falls back to
a deterministic keyword-based intent classifier so the app remains usable
without a key, clearly flagged as a fallback in the response).
