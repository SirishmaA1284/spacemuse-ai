# Model Selection

| Task | Model | Rationale |
|---|---|---|
| Intent detection | `gemini-3.6-flash` (configurable via `GEMINI_MODEL`, current default as of Aug 2026) | Low latency, structured-output classification doesn't need Pro-tier reasoning |
| Room/design reasoning (planned) | `gemini-3.6-pro` or current Pro-tier equivalent | Multimodal, higher reasoning quality needed for spatial/design judgment |
| Image generation/editing (planned) | Gemini image-capable model (exact model id TBD at implementation time — check current Google AI docs, do not assume a fixed id) | Visualization must edit specific regions, not regenerate the whole image |
| Local personalization | Gemma (on-device, quantized) | Must run offline, privacy-sensitive |

Default model id is a `.env`-configurable value (`GEMINI_MODEL`), never
hardcoded in business logic, because Google's available model ids change
over time — always check current official docs before pinning a new default.
Note: `gemini-2.5-flash`/`gemini-2.5-flash-lite` are scheduled to shut down
on the Gemini Developer API on 2026-10-16 — do not reintroduce them as a
default. Check https://ai.google.dev/gemini-api/docs/models for the current
lineup before any deploy.
