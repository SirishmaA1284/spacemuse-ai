# Gemma — On-Device Intelligence

## Role

Gemma runs entirely on-device (Android `ai/` module) and is responsible for
capabilities that should work offline and keep sensitive data local:

- Local preference/personalization reasoning
- Lightweight conversational context between app sessions
- Local memory retrieval (what the user has said they like/dislike)
- Local design-rule checks that don't require the full room image

Gemma is **not** used for room image understanding or product image
reasoning — that requires Gemini's multimodal capability server-side.

## Integration path

Planned via Google AI Edge's **MediaPipe LLM Inference API**, loading a
quantized Gemma `.task` model bundle on-device. No API key is required —
this is a local model file, not a network call.

`android/ai/` currently contains the module scaffold and the interface
Gemma inference will implement (`GemmaLocalReasoner`), but **model loading
and inference are not yet wired** — see `docs/development/roadmap.md` Phase 6
and `docs/development/technical-debt.md`.

## Local memory controls

Per section 39/51/66 of the product spec, the user must be able to view,
edit, delete, and disable local personalization. UI hooks for this live in
the (planned) `android/preferences/` module; the underlying local store is
Android DataStore, never synced to the backend unless the user explicitly
opts into cross-device sync.

## Offline mode

When the network is unavailable, the app must clearly communicate which
features degrade (see `docs/architecture/system-architecture.md` table) —
never silently claim full functionality.
