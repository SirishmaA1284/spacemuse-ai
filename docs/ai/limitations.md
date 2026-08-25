# Known AI Limitations

- **Room-photo understanding is wired but unevaluated.** `rooms/analyze`
  calls Gemini vision when an image + `GEMINI_API_KEY` are present
  (`backend/src/agents/roomAnalysisAgent.ts`), but no accuracy evaluation
  has been run against real room photos yet (`docs/ai/evaluation.md` has no
  entries) — treat classifications and dimension estimates as unverified
  until that exists. Without an image (or without a key, or if the model's
  response fails schema validation), the endpoint returns a fixed demo
  `RoomAnalysis` tagged `source: "demo"` rather than failing.
- **Without `GEMINI_API_KEY`**, intent detection silently falls back to a
  small keyword-based classifier (`backend/src/agents/intentAgent.ts`). This
  is intentional (keeps the app usable in dev without a key) but is far less
  accurate than the real model — the API response includes
  `"source": "fallback"` so callers/UI can indicate degraded mode.
- **No uncertainty calibration exists yet** — once Gemini room analysis is
  wired, confidence scores from the model are not independently validated
  against ground truth. Treat them as the model's self-reported confidence,
  not a calibrated probability.
- **No hallucination guardrails on product data yet** — this is why product
  data only ever comes from a `ProductProvider` (never free-text LLM output)
  once shopping agents are implemented; the schema forces `source` and
  `lastUpdated` on every `Product` record.
