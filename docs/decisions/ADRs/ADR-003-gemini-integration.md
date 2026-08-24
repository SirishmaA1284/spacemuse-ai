# ADR-003: Gemini Developer API (not Vertex AI) for v0

## Context
Need cloud multimodal reasoning. Google offers two auth paths: the Gemini
Developer API (simple API key, via Google AI Studio) and Vertex AI (GCP
service account, IAM-scoped).

## Options considered
- Gemini Developer API (`GEMINI_API_KEY`)
- Vertex AI (`GOOGLE_CLOUD_PROJECT_ID` + service account / ADC)

## Decision
Start with the Gemini Developer API for v0; document Vertex AI as the
planned production migration path.

## Reason
Developer API has a much faster setup loop (single key, no GCP project
provisioning) appropriate for the current build-and-verify stage. Vertex AI
gives better production characteristics (IAM scoping, VPC controls, org
billing) that matter once this handles real user data — deferring that
migration is a documented, deliberate choice, not an oversight.

## Consequences
- `backend/src/ai/gemini/geminiClient.ts` is written against the
  `@google/genai` SDK's API-key auth path.
- Migrating to Vertex later requires swapping the client's auth
  initialization, not its call sites — client is already isolated behind a
  single wrapper module for this reason.
- Both `GEMINI_API_KEY` and `GOOGLE_CLOUD_PROJECT_ID` are present in
  `.env.example` so the migration doesn't require a new onboarding doc.
