# ADR-002: Gemma via MediaPipe LLM Inference (On-Device)

## Context
Product spec requires meaningful, privacy-preserving on-device intelligence
(section 39) distinct from cloud Gemini reasoning.

## Options considered
- Gemma via Google AI Edge / MediaPipe LLM Inference API (on-device `.task` model)
- Gemma via a self-hosted server endpoint (defeats "on-device" requirement)
- Skipping on-device inference, doing everything via Gemini

## Decision
Gemma via MediaPipe LLM Inference, running a quantized model bundle
on-device, scoped to local personalization/memory/lightweight context tasks
only — not room image understanding.

## Reason
This is the only option that satisfies the privacy requirement (spec
section 66: sensitive personalization data should not need to leave the
device) and the offline-availability requirement (section 43). It does not
handle multimodal room reasoning — that stays with Gemini server-side, where
the heavier model lives.

## Consequences
- Requires bundling/downloading a quantized Gemma model file on first run
  (size/UX tradeoff to manage at implementation time).
- Local inference is meaningfully less capable than Gemini — must not be
  used for tasks it can't reliably do (room/image understanding).
- Not yet implemented in code (`android/ai/` has the module + interface
  only) — tracked in `docs/development/roadmap.md` Phase 6.
